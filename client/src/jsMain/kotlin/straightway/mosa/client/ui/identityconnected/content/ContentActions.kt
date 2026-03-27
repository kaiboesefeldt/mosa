package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

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

internal data class ContentActions(
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

internal fun buildContentActions(
    dependencies: ContentActionDependencies,
    topics: ContentTopicsModel,
    composer: ContentComposerModel,
    deletion: ContentDeletionModel,
    postFeed: ContentPostFeedModel,
): ContentActions =
    ContentActions(
        addTopic = {
            val sanitized = (window.prompt(dependencies.context.async.translator.translate("connected.topics.prompt")) ?: "").trim()
            if (sanitized.isEmpty()) return@ContentActions
            dependencies.context.async.scope.launch {
                val updated = dependencies.repository.add(sanitized)
                topics.updateTopics(updated)
                topics.selectTopic(updated.lastOrNull())
            }
        },
        removeTopic = { topic ->
            val removalIndex = topics.topics.indexOfFirst { it.title == topic.title }
            val wasSelected = topics.selectedTopic?.title == topic.title
            if (removalIndex < 0) return@ContentActions
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
            if (composer.isPublishing) return@ContentActions
            composer.setError(null)
            composer.setOpen(false)
        },
        sendPost = {
            if (composer.isPublishing) return@ContentActions
            val text = composer.text.trim()
            if (text.isEmpty()) return@ContentActions
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
            if (deletion.isDeletingPost) return@ContentActions
            deletion.setCandidate(entry)
            deletion.setError(null)
        },
        clearDeleteCandidate = {
            if (deletion.isDeletingPost) return@ContentActions
            deletion.setCandidate(null)
            deletion.setError(null)
        },
        confirmDelete = { entry ->
            if (deletion.isDeletingPost) return@ContentActions
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
            if (deletion.isDeletingAllPosts) return@ContentActions
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
            val previousFilter = topics.topicFilters[topic.title] ?: return@ContentActions
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
            val topic = topics.selectedTopic ?: return@ContentActions
            if (postFeed.isLoadingPosts || postFeed.isAppendingPosts) return@ContentActions
            val currentPosts = postFeed.posts.firstOrNull { it.topic.title == topic.title } ?: return@ContentActions
            if (currentPosts.entries.isEmpty()) return@ContentActions
            val hasAnchorEntry =
                currentPosts.entries.any { entry ->
                    val threshold = topic.scoreMustBeAbove
                    threshold == null || entry.similarity > threshold
                }
            if (!hasAnchorEntry) return@ContentActions
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
