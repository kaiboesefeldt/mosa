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
    topics: IdentityConnectedViewContentTopicsModel,
    composer: IdentityConnectedViewContentComposerModel,
    deletion: IdentityConnectedViewContentDeletionModel,
    postFeed: IdentityConnectedViewContentPostFeedModel,
): IdentityConnectedViewContentActions =
    IdentityConnectedViewContentActions(
        addTopic = {
            val sanitized = (window.prompt(dependencies.context.async.translator.translate("connected.topics.prompt")) ?: "").trim()
            if (sanitized.isEmpty()) return@IdentityConnectedViewContentActions
            dependencies.context.async.scope.launch {
                val updated = dependencies.repository.add(sanitized)
                topics.updateTopics(updated)
                topics.selectTopic(updated.lastOrNull())
            }
        },
        removeTopic = { topic ->
            val removalIndex = topics.topics.indexOfFirst { it.title == topic.title }
            val wasSelected = topics.selectedTopic?.title == topic.title
            if (removalIndex < 0) return@IdentityConnectedViewContentActions
            dependencies.context.async.scope.launch {
                val updated = dependencies.repository.remove(topic)
                topics.updateTopics(updated)
                if (topics.topicFilters.containsKey(topic.title)) {
                    topics.persistFilters(topics.topicFilters - topic.title)
                }
                if (wasSelected) topics.selectTopic(updated.getOrNull(removalIndex))
            }
        },
        openComposer = {
            composer.setError(null)
            composer.setOpen(true)
        },
        closeComposer = {
            if (composer.isPublishing) return@IdentityConnectedViewContentActions
            composer.setError(null)
            composer.setOpen(false)
        },
        sendPost = {
            if (composer.isPublishing) return@IdentityConnectedViewContentActions
            val text = composer.text.trim()
            if (text.isEmpty()) return@IdentityConnectedViewContentActions
            val topicForRefresh = topics.selectedTopic
            composer.setPublishing(true)
            composer.setError(null)
            dependencies.context.async.scope.launch {
                try {
                    val success = dependencies.postPublisher.publish(text)
                    if (success) {
                        composer.setText("")
                        composer.setOpen(false)
                        topicForRefresh?.let { topic -> runCatching { dependencies.postCache.clear(topic.title) } }
                        val refreshed = postFeed.refreshPosts(topicForRefresh)
                        if (!refreshed && topics.selectedTopic?.title == topicForRefresh?.title) {
                            dependencies.context.showToast(
                                dependencies.context.async.translator.translate("connected.composer.refresh.error"),
                            )
                        }
                    } else {
                        composer.setError(dependencies.context.async.translator.translate("connected.composer.error"))
                    }
                } finally {
                    composer.setPublishing(false)
                }
            }
        },
        requestDelete = { entry ->
            if (deletion.isDeletingPost) return@IdentityConnectedViewContentActions
            deletion.setCandidate(entry)
            deletion.setError(null)
        },
        clearDeleteCandidate = {
            if (deletion.isDeletingPost) return@IdentityConnectedViewContentActions
            deletion.setCandidate(null)
            deletion.setError(null)
        },
        confirmDelete = { entry ->
            if (deletion.isDeletingPost) return@IdentityConnectedViewContentActions
            deletion.setDeletingPost(true)
            deletion.setError(null)
            dependencies.context.async.scope.launch {
                val success = dependencies.postDeletionService.delete(entry.id)
                deletion.setDeletingPost(false)
                if (success) {
                    deletion.removePost(entry.id)
                } else {
                    deletion.setError(dependencies.context.async.translator.translate("connected.posts.delete.error"))
                }
            }
        },
        deleteAllPosts = {
            if (deletion.isDeletingAllPosts) return@IdentityConnectedViewContentActions
            deletion.setDeletingAllPosts(true)
            dependencies.context.async.scope.launch {
                val deletedIds = dependencies.allPostsDeletionService.deleteAll()
                deletion.setDeletingAllPosts(false)
                if (deletedIds == null) {
                    dependencies.context.showToast(dependencies.context.async.translator.translate("identity.posts.delete.error"))
                } else {
                    deletion.removePosts(deletedIds)
                    dependencies.context.showToast(dependencies.context.async.translator.translate("identity.posts.delete.success"))
                }
            }
        },
        clearTopicFilter = { topic ->
            val previousFilter = topics.topicFilters[topic.title] ?: return@IdentityConnectedViewContentActions
            topics.persistFilters(topics.topicFilters - topic.title)
            val clearedTopic = topic.copy(scoreMustBeAbove = null)
            dependencies.context.async.scope.launch {
                dependencies.searchService.filterCleared(clearedTopic, previousFilter)
                if (topics.selectedTopic?.title == topic.title) postFeed.refreshPosts(clearedTopic)
            }
        },
        toggleTopicFilter = { topic, entry ->
            val currentScore = topics.topicFilters[topic.title]
            if (currentScore != null && currentScore == entry.similarity) {
                val previousFilter = currentScore
                topics.persistFilters(topics.topicFilters - topic.title)
                val clearedTopic = topic.copy(scoreMustBeAbove = null)
                dependencies.context.async.scope.launch {
                    dependencies.searchService.filterCleared(clearedTopic, previousFilter)
                    if (topics.selectedTopic?.title == topic.title) postFeed.refreshPosts(clearedTopic)
                }
            } else {
                topics.persistFilters(topics.topicFilters + (topic.title to entry.similarity))
            }
        },
        loadMorePosts = {
            val topic = topics.selectedTopic ?: return@IdentityConnectedViewContentActions
            if (postFeed.isLoadingPosts || postFeed.isAppendingPosts) return@IdentityConnectedViewContentActions
            val currentPosts = postFeed.posts.firstOrNull { it.topic.title == topic.title } ?: return@IdentityConnectedViewContentActions
            if (currentPosts.entries.isEmpty()) return@IdentityConnectedViewContentActions
            val hasAnchorEntry =
                currentPosts.entries.any { entry ->
                    val threshold = topic.scoreMustBeAbove
                    threshold == null || entry.similarity > threshold
                }
            if (!hasAnchorEntry) return@IdentityConnectedViewContentActions
            dependencies.postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
            postFeed.setAppending(true)
            dependencies.context.async.scope.launch {
                try {
                    dependencies.searchService.fetchMore(topic)?.let { additional ->
                        postFeed.updatePosts(replaceTopicPosts(postFeed.posts, additional))
                    }
                } finally {
                    postFeed.setAppending(false)
                }
            }
        },
    )
