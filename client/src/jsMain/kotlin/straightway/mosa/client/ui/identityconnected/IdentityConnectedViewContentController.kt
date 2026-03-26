package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.browser.window
import kotlinx.coroutines.launch
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicFilters
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.content.QueryResult

private val noContentDependencies: Array<dynamic> = emptyArray()

internal fun useIdentityConnectedViewContentController(
    dependencies: IdentityConnectedViewContentControllerDependencies,
    callbacks: IdentityConnectedViewContentControllerCallbacks,
): IdentityConnectedViewContentController {
    var topics by useState<List<Topic>>(emptyList())
    var selectedTopic by useState<Topic?>(null)
    var posts by useState<List<TopicPosts>>(emptyList())
    var topicFilters by useState<Map<String, Float>>(emptyMap())
    var isLoadingPosts by useState(false)
    var isComposerOpen by useState(false)
    var composerText by useState("")
    var isPublishing by useState(false)
    var publishError by useState<String?>(null)
    var deleteCandidate by useState<QueryResult.Entry?>(null)
    var isDeletingPost by useState(false)
    var isDeletingAllPosts by useState(false)
    var deleteError by useState<String?>(null)
    val postsRequestVersion = useRef(0)
    val isAppendingPostsRef = useRef(false)
    val snapshotRefreshVersionRef = useRef(dependencies.snapshotRefreshVersion)
    val replyController =
        useIdentityConnectedViewReplyController(
                IdentityConnectedViewReplyControllerDependencies(
                    context = dependencies.context.async,
                    replyPublisher = dependencies.replyPublisher,
                    replySearchService = dependencies.replySearchService,
                    repliesContainerRef = dependencies.refs.repliesContainerRef,
                    onPostPaneRequested = callbacks.onPostPaneRequested,
                ),
        )

    fun setAppendingState(active: Boolean) {
        isAppendingPostsRef.current = active
    }

    fun updateTopicFilterState(filters: Map<String, Float>) {
        if (topics.isEmpty()) return
        val nextTopics =
            topics.map { topic ->
                val nextFilter = filters[topic.title]
                if (topic.scoreMustBeAbove == nextFilter) topic else topic.copy(scoreMustBeAbove = nextFilter)
            }
        if (nextTopics == topics) return
        topics = nextTopics
        selectedTopic = selectedTopic?.title?.let { title -> nextTopics.firstOrNull { it.title == title } }
    }

    suspend fun refreshPosts(currentTopic: Topic?): Boolean {
        val nextVersion = (postsRequestVersion.current ?: 0) + 1
        postsRequestVersion.current = nextVersion
        dependencies.refs.postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        if (currentTopic == null) {
            posts = emptyList()
            isLoadingPosts = false
            return true
        }
        setAppendingState(false)
        isLoadingPosts = true
        val results =
            runCatching { dependencies.searchService.fetchFor(listOf(currentTopic)) }
                .getOrElse {
                    if (postsRequestVersion.current == nextVersion) isLoadingPosts = false
                    return false
                }
        if (postsRequestVersion.current != nextVersion) return false
        posts = results
        isLoadingPosts = false
        return true
    }

    fun persistFilters(updated: Map<String, Float>) {
        val sanitized = TopicFilters.sanitize(updated)
        if (sanitized == topicFilters) return
        updateTopicFilterState(sanitized)
        topicFilters = sanitized
        dependencies.context.async.scope.launch { dependencies.repository.applyFilters(sanitized) }
    }

    fun selectTopic(topic: Topic?) {
        if (topic == selectedTopic) {
            if (dependencies.isMobile && topic != null) {
                replyController.clearReplies()
                callbacks.onTopicPaneRequested(topic)
            }
            return
        }
        replyController.clearReplies()
        callbacks.onTopicPaneRequested(topic)
        selectedTopic = topic
        dependencies.context.async.scope.launch {
            callbacks.persistSessionSnapshot { snapshot ->
                IdentitySessionSnapshot(snapshot?.connectedIdentityGid, snapshot?.isProfileVisible ?: false, topic?.title)
            }
            refreshPosts(topic)
        }
    }

    fun removePost(postId: String) {
        posts = removePostsFromTopicState(posts, setOf(postId))
        replyController.dropDeletedPosts(setOf(postId))
        if (deleteCandidate?.id == postId) deleteCandidate = null
    }

    fun removePosts(postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        val idSet = postIds.toSet()
        posts = removePostsFromTopicState(posts, idSet)
        replyController.dropDeletedPosts(idSet)
        if (deleteCandidate?.id in idSet) deleteCandidate = null
    }

    useEffect(dependencies.snapshotRefreshVersion) {
        if (dependencies.snapshotRefreshVersion == snapshotRefreshVersionRef.current) return@useEffect
        snapshotRefreshVersionRef.current = dependencies.snapshotRefreshVersion
        val topicForRefresh = selectedTopic
        replyController.clearReplies()
        dependencies.context.async.scope.launch {
            dependencies.postCache.clearAll()
            dependencies.replyCache.clearAll()
            val refreshed = refreshPosts(topicForRefresh)
            if (!refreshed && selectedTopic?.title == topicForRefresh?.title) {
                dependencies.context.showToast(
                    dependencies.context.async.translator.translate("connected.composer.refresh.error"),
                )
            }
        }
    }

    useEffect(*noContentDependencies) {
        dependencies.context.async.scope.launch {
            val loadedTopics = dependencies.repository.all()
            topics = loadedTopics
            topicFilters = extractTopicFilters(loadedTopics)
            val storedSelection = runCatching { dependencies.sessionStore.load() }.getOrNull()?.selectedTopicTitle
            selectTopic(loadedTopics.firstOrNull { it.title == storedSelection } ?: loadedTopics.firstOrNull())
        }
    }

    val contentActions =
        buildIdentityConnectedViewContentActions(
            dependencies =
                IdentityConnectedViewContentActionDependencies(
                    context = dependencies.context,
                    repository = dependencies.repository,
                    searchService = dependencies.searchService,
                    postCache = dependencies.postCache,
                    postPublisher = dependencies.postPublisher,
                    postDeletionService = dependencies.postDeletionService,
                    allPostsDeletionService = dependencies.allPostsDeletionService,
                    postsContainerRef = dependencies.refs.postsContainerRef,
                ),
            state =
                IdentityConnectedViewContentActionState(
                    topics = { topics },
                    selectedTopic = { selectedTopic },
                    topicFilters = { topicFilters },
                    posts = { posts },
                    isLoadingPosts = { isLoadingPosts },
                    isAppendingPosts = { isAppendingPostsRef.current == true },
                    isPublishing = { isPublishing },
                    isDeletingPost = { isDeletingPost },
                    isDeletingAllPosts = { isDeletingAllPosts },
                    composerText = { composerText },
                ),
            mutations =
                IdentityConnectedViewContentActionMutations(
                    setTopics = { topics = it },
                    setPosts = { posts = it },
                    selectTopic = ::selectTopic,
                    persistFilters = ::persistFilters,
                    refreshPosts = ::refreshPosts,
                    setAppendingState = ::setAppendingState,
                    setComposerOpen = { isComposerOpen = it },
                    setComposerText = { composerText = it },
                    setPublishing = { isPublishing = it },
                    setPublishError = { publishError = it },
                    setDeleteCandidate = { deleteCandidate = it },
                    setDeleteError = { deleteError = it },
                    setDeletingPost = { isDeletingPost = it },
                    setDeletingAllPosts = { isDeletingAllPosts = it },
                    removePost = ::removePost,
                    removePosts = ::removePosts,
                ),
        )

    return buildIdentityConnectedViewContentController(
        state =
            IdentityConnectedViewContentControllerState(
                topics = topics,
                selectedTopic = selectedTopic,
                posts = posts,
                selectedPost = replyController.selectedPost,
                replies = replyController.replies,
                isLoadingReplies = replyController.isLoadingReplies,
                topicFilters = topicFilters,
                isLoadingPosts = isLoadingPosts,
                isComposerOpen = isComposerOpen,
                composerText = composerText,
                isPublishing = isPublishing,
                publishError = publishError,
                deleteCandidate = deleteCandidate,
                isDeletingPost = isDeletingPost,
                isDeletingAllPosts = isDeletingAllPosts,
                deleteError = deleteError,
                isReplyComposerOpen = replyController.isReplyComposerOpen,
                replyComposerText = replyController.replyComposerText,
                isReplyPublishing = replyController.isReplyPublishing,
                replyPublishError = replyController.replyPublishError,
            ),
        actions =
            IdentityConnectedViewContentControllerActions(
                setComposerText = { composerText = it },
                setReplyComposerText = replyController.setReplyComposerText,
                addTopic = contentActions.addTopic,
                removeTopic = contentActions.removeTopic,
                selectTopic = ::selectTopic,
                openComposer = contentActions.openComposer,
                closeComposer = contentActions.closeComposer,
                sendPost = contentActions.sendPost,
                requestDelete = contentActions.requestDelete,
                clearDeleteCandidate = contentActions.clearDeleteCandidate,
                confirmDelete = contentActions.confirmDelete,
                deleteAllPosts = contentActions.deleteAllPosts,
                openReplyComposer = replyController.openReplyComposer,
                closeReplyComposer = replyController.closeReplyComposer,
                sendReply = replyController.sendReply,
                clearTopicFilter = contentActions.clearTopicFilter,
                toggleTopicFilter = contentActions.toggleTopicFilter,
                loadMorePosts = contentActions.loadMorePosts,
                loadMoreReplies = replyController.loadMoreReplies,
                selectPost = replyController.selectPost,
                clearReplies = replyController.clearReplies,
            ),
        lookups =
            IdentityConnectedViewContentControllerLookups(
                findTopicByTitle = { title -> findTopicByTitle(topics, title) },
                findPostById = { id -> findPostById(posts, id) },
            ),
    )
}
