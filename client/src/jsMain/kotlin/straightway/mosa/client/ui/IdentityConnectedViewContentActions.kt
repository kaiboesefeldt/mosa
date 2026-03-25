package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.RefObject
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.QueryResult
import web.html.HTMLDivElement

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
    translator: Translator,
    repository: TopicRepository,
    searchService: TopicSearchService,
    postCache: PostCache,
    postPublisher: PostPublisher,
    postDeletionService: PostDeletionService,
    allPostsDeletionService: AllPostsDeletionService,
    scope: CoroutineScope,
    postsContainerRef: RefObject<HTMLDivElement>,
    showToast: (String) -> Unit,
    topics: () -> List<Topic>,
    selectedTopic: () -> Topic?,
    topicFilters: () -> Map<String, Float>,
    posts: () -> List<TopicPosts>,
    isLoadingPosts: () -> Boolean,
    isAppendingPosts: () -> Boolean,
    isPublishing: () -> Boolean,
    isDeletingPost: () -> Boolean,
    isDeletingAllPosts: () -> Boolean,
    setTopics: (List<Topic>) -> Unit,
    setPosts: (List<TopicPosts>) -> Unit,
    selectTopic: (Topic?) -> Unit,
    persistFilters: (Map<String, Float>) -> Unit,
    refreshPosts: suspend (Topic?) -> Boolean,
    setAppendingState: (Boolean) -> Unit,
    setComposerOpen: (Boolean) -> Unit,
    setComposerText: (String) -> Unit,
    composerText: () -> String,
    setPublishing: (Boolean) -> Unit,
    setPublishError: (String?) -> Unit,
    setDeleteCandidate: (QueryResult.Entry?) -> Unit,
    setDeleteError: (String?) -> Unit,
    setDeletingPost: (Boolean) -> Unit,
    setDeletingAllPosts: (Boolean) -> Unit,
    removePost: (String) -> Unit,
    removePosts: (Collection<String>) -> Unit,
): IdentityConnectedViewContentActions =
    IdentityConnectedViewContentActions(
        addTopic = {
            val sanitized = (window.prompt(translator.translate("connected.topics.prompt")) ?: "").trim()
            if (sanitized.isEmpty()) return@IdentityConnectedViewContentActions
            scope.launch {
                val updated = repository.add(sanitized)
                setTopics(updated)
                selectTopic(updated.lastOrNull())
            }
        },
        removeTopic = { topic ->
            val removalIndex = topics().indexOfFirst { it.title == topic.title }
            val wasSelected = selectedTopic()?.title == topic.title
            if (removalIndex < 0) return@IdentityConnectedViewContentActions
            scope.launch {
                val updated = repository.remove(topic)
                setTopics(updated)
                if (topicFilters().containsKey(topic.title)) persistFilters(topicFilters() - topic.title)
                if (wasSelected) selectTopic(updated.getOrNull(removalIndex))
            }
        },
        openComposer = {
            setPublishError(null)
            setComposerOpen(true)
        },
        closeComposer = {
            if (isPublishing()) return@IdentityConnectedViewContentActions
            setPublishError(null)
            setComposerOpen(false)
        },
        sendPost = {
            if (isPublishing()) return@IdentityConnectedViewContentActions
            val text = composerText().trim()
            if (text.isEmpty()) return@IdentityConnectedViewContentActions
            val topicForRefresh = selectedTopic()
            setPublishing(true)
            setPublishError(null)
            scope.launch {
                try {
                    val success = postPublisher.publish(text)
                    if (success) {
                        setComposerText("")
                        setComposerOpen(false)
                        topicForRefresh?.let { topic -> runCatching { postCache.clear(topic.title) } }
                        val refreshed = refreshPosts(topicForRefresh)
                        if (!refreshed && selectedTopic()?.title == topicForRefresh?.title) {
                            showToast(translator.translate("connected.composer.refresh.error"))
                        }
                    } else {
                        setPublishError(translator.translate("connected.composer.error"))
                    }
                } finally {
                    setPublishing(false)
                }
            }
        },
        requestDelete = { entry ->
            if (isDeletingPost()) return@IdentityConnectedViewContentActions
            setDeleteCandidate(entry)
            setDeleteError(null)
        },
        clearDeleteCandidate = {
            if (isDeletingPost()) return@IdentityConnectedViewContentActions
            setDeleteCandidate(null)
            setDeleteError(null)
        },
        confirmDelete = { entry ->
            if (isDeletingPost()) return@IdentityConnectedViewContentActions
            setDeletingPost(true)
            setDeleteError(null)
            scope.launch {
                val success = postDeletionService.delete(entry.id)
                setDeletingPost(false)
                if (success) removePost(entry.id) else setDeleteError(translator.translate("connected.posts.delete.error"))
            }
        },
        deleteAllPosts = {
            if (isDeletingAllPosts()) return@IdentityConnectedViewContentActions
            setDeletingAllPosts(true)
            scope.launch {
                val deletedIds = allPostsDeletionService.deleteAll()
                setDeletingAllPosts(false)
                if (deletedIds == null) {
                    showToast(translator.translate("identity.posts.delete.error"))
                } else {
                    removePosts(deletedIds)
                    showToast(translator.translate("identity.posts.delete.success"))
                }
            }
        },
        clearTopicFilter = { topic ->
            val previousFilter = topicFilters()[topic.title] ?: return@IdentityConnectedViewContentActions
            persistFilters(topicFilters() - topic.title)
            val clearedTopic = topic.copy(scoreMustBeAbove = null)
            scope.launch {
                searchService.filterCleared(clearedTopic, previousFilter)
                if (selectedTopic()?.title == topic.title) refreshPosts(clearedTopic)
            }
        },
        toggleTopicFilter = { topic, entry ->
            val currentScore = topicFilters()[topic.title]
            if (currentScore != null && currentScore == entry.similarity) {
                val previousFilter = currentScore
                persistFilters(topicFilters() - topic.title)
                val clearedTopic = topic.copy(scoreMustBeAbove = null)
                scope.launch {
                    searchService.filterCleared(clearedTopic, previousFilter)
                    if (selectedTopic()?.title == topic.title) refreshPosts(clearedTopic)
                }
            } else {
                persistFilters(topicFilters() + (topic.title to entry.similarity))
            }
        },
        loadMorePosts = {
            val topic = selectedTopic() ?: return@IdentityConnectedViewContentActions
            if (isLoadingPosts() || isAppendingPosts()) return@IdentityConnectedViewContentActions
            val currentPosts = posts().firstOrNull { it.topic.title == topic.title } ?: return@IdentityConnectedViewContentActions
            if (currentPosts.entries.isEmpty()) return@IdentityConnectedViewContentActions
            val hasAnchorEntry = currentPosts.entries.any { entry ->
                val threshold = topic.scoreMustBeAbove
                threshold == null || entry.similarity > threshold
            }
            if (!hasAnchorEntry) return@IdentityConnectedViewContentActions
            postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
            setAppendingState(true)
            scope.launch {
                try {
                    searchService.fetchMore(topic)?.let { additional ->
                        setPosts(replaceTopicPosts(posts(), additional))
                    }
                } finally {
                    setAppendingState(false)
                }
            }
        },
    )
