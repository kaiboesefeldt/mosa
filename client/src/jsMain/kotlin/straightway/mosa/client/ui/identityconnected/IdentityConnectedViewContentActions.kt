package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.browser.window
import kotlinx.coroutines.launch
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.content.QueryResult

internal data class IdentityConnectedViewContentActions(
    val addTopic: () -> Unit,
    val removeTopic: (Topic) -> Unit,
    val openComposer: () -> Unit,
    val closeComposer: () -> Unit,
    val sendPost: () -> Unit,
    val requestDelete: (QueryResult.Entry) -> Unit,
    val clearDeleteCandidate: () -> Unit,
    val confirmDelete: (QueryResult.Entry) -> Unit,
    val deleteAllPosts: () -> Unit,
    val clearTopicFilter: (Topic) -> Unit,
    val toggleTopicFilter: (Topic, QueryResult.Entry) -> Unit,
    val loadMorePosts: () -> Unit,
)

internal fun buildIdentityConnectedViewContentActions(
    dependencies: IdentityConnectedViewContentActionDependencies,
    state: IdentityConnectedViewContentActionState,
    mutations: IdentityConnectedViewContentActionMutations,
): IdentityConnectedViewContentActions =
    IdentityConnectedViewContentActions(
        addTopic = {
            val sanitized = (window.prompt(dependencies.context.async.translator.translate("connected.topics.prompt")) ?: "").trim()
            if (sanitized.isEmpty()) return@IdentityConnectedViewContentActions
            dependencies.context.async.scope.launch {
                val updated = dependencies.repository.add(sanitized)
                mutations.setTopics(updated)
                mutations.selectTopic(updated.lastOrNull())
            }
        },
        removeTopic = { topic ->
            val removalIndex = state.topics().indexOfFirst { it.title == topic.title }
            val wasSelected = state.selectedTopic()?.title == topic.title
            if (removalIndex < 0) return@IdentityConnectedViewContentActions
            dependencies.context.async.scope.launch {
                val updated = dependencies.repository.remove(topic)
                mutations.setTopics(updated)
                if (state.topicFilters().containsKey(topic.title)) {
                    mutations.persistFilters(state.topicFilters() - topic.title)
                }
                if (wasSelected) mutations.selectTopic(updated.getOrNull(removalIndex))
            }
        },
        openComposer = {
            mutations.setPublishError(null)
            mutations.setComposerOpen(true)
        },
        closeComposer = {
            if (state.isPublishing()) return@IdentityConnectedViewContentActions
            mutations.setPublishError(null)
            mutations.setComposerOpen(false)
        },
        sendPost = {
            if (state.isPublishing()) return@IdentityConnectedViewContentActions
            val text = state.composerText().trim()
            if (text.isEmpty()) return@IdentityConnectedViewContentActions
            val topicForRefresh = state.selectedTopic()
            mutations.setPublishing(true)
            mutations.setPublishError(null)
            dependencies.context.async.scope.launch {
                try {
                    val success = dependencies.postPublisher.publish(text)
                    if (success) {
                        mutations.setComposerText("")
                        mutations.setComposerOpen(false)
                        topicForRefresh?.let { topic -> runCatching { dependencies.postCache.clear(topic.title) } }
                        val refreshed = mutations.refreshPosts(topicForRefresh)
                        if (!refreshed && state.selectedTopic()?.title == topicForRefresh?.title) {
                            dependencies.context.showToast(
                                dependencies.context.async.translator.translate("connected.composer.refresh.error"),
                            )
                        }
                    } else {
                        mutations.setPublishError(dependencies.context.async.translator.translate("connected.composer.error"))
                    }
                } finally {
                    mutations.setPublishing(false)
                }
            }
        },
        requestDelete = { entry ->
            if (state.isDeletingPost()) return@IdentityConnectedViewContentActions
            mutations.setDeleteCandidate(entry)
            mutations.setDeleteError(null)
        },
        clearDeleteCandidate = {
            if (state.isDeletingPost()) return@IdentityConnectedViewContentActions
            mutations.setDeleteCandidate(null)
            mutations.setDeleteError(null)
        },
        confirmDelete = { entry ->
            if (state.isDeletingPost()) return@IdentityConnectedViewContentActions
            mutations.setDeletingPost(true)
            mutations.setDeleteError(null)
            dependencies.context.async.scope.launch {
                val success = dependencies.postDeletionService.delete(entry.id)
                mutations.setDeletingPost(false)
                if (success) {
                    mutations.removePost(entry.id)
                } else {
                    mutations.setDeleteError(dependencies.context.async.translator.translate("connected.posts.delete.error"))
                }
            }
        },
        deleteAllPosts = {
            if (state.isDeletingAllPosts()) return@IdentityConnectedViewContentActions
            mutations.setDeletingAllPosts(true)
            dependencies.context.async.scope.launch {
                val deletedIds = dependencies.allPostsDeletionService.deleteAll()
                mutations.setDeletingAllPosts(false)
                if (deletedIds == null) {
                    dependencies.context.showToast(dependencies.context.async.translator.translate("identity.posts.delete.error"))
                } else {
                    mutations.removePosts(deletedIds)
                    dependencies.context.showToast(dependencies.context.async.translator.translate("identity.posts.delete.success"))
                }
            }
        },
        clearTopicFilter = { topic ->
            val previousFilter = state.topicFilters()[topic.title] ?: return@IdentityConnectedViewContentActions
            mutations.persistFilters(state.topicFilters() - topic.title)
            val clearedTopic = topic.copy(scoreMustBeAbove = null)
            dependencies.context.async.scope.launch {
                dependencies.searchService.filterCleared(clearedTopic, previousFilter)
                if (state.selectedTopic()?.title == topic.title) mutations.refreshPosts(clearedTopic)
            }
        },
        toggleTopicFilter = { topic, entry ->
            val currentScore = state.topicFilters()[topic.title]
            if (currentScore != null && currentScore == entry.similarity) {
                val previousFilter = currentScore
                mutations.persistFilters(state.topicFilters() - topic.title)
                val clearedTopic = topic.copy(scoreMustBeAbove = null)
                dependencies.context.async.scope.launch {
                    dependencies.searchService.filterCleared(clearedTopic, previousFilter)
                    if (state.selectedTopic()?.title == topic.title) mutations.refreshPosts(clearedTopic)
                }
            } else {
                mutations.persistFilters(state.topicFilters() + (topic.title to entry.similarity))
            }
        },
        loadMorePosts = {
            val topic = state.selectedTopic() ?: return@IdentityConnectedViewContentActions
            if (state.isLoadingPosts() || state.isAppendingPosts()) return@IdentityConnectedViewContentActions
            val currentPosts = state.posts().firstOrNull { it.topic.title == topic.title } ?: return@IdentityConnectedViewContentActions
            if (currentPosts.entries.isEmpty()) return@IdentityConnectedViewContentActions
            val hasAnchorEntry =
                currentPosts.entries.any { entry ->
                    val threshold = topic.scoreMustBeAbove
                    threshold == null || entry.similarity > threshold
                }
            if (!hasAnchorEntry) return@IdentityConnectedViewContentActions
            dependencies.postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
            mutations.setAppendingState(true)
            dependencies.context.async.scope.launch {
                try {
                    dependencies.searchService.fetchMore(topic)?.let { additional ->
                        mutations.setPosts(replaceTopicPosts(state.posts(), additional))
                    }
                } finally {
                    mutations.setAppendingState(false)
                }
            }
        },
    )
