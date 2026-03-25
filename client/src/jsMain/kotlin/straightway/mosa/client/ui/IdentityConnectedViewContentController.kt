package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.RefObject
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
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.QueryResult
import web.html.HTMLDivElement

private val noContentDependencies: Array<dynamic> = emptyArray()

internal fun useIdentityConnectedViewContentController(
    translator: Translator,
    repository: TopicRepository,
    sessionStore: IdentitySessionStore,
    searchService: TopicSearchService,
    postCache: PostCache,
    replyCache: ReplyCache,
    postPublisher: PostPublisher,
    postDeletionService: PostDeletionService,
    allPostsDeletionService: AllPostsDeletionService,
    replyPublisher: ReplyPublisher,
    replySearchService: ReplySearchService,
    scope: CoroutineScope,
    isMobile: Boolean,
    snapshotRefreshVersion: Int,
    postsContainerRef: RefObject<HTMLDivElement>,
    repliesContainerRef: RefObject<HTMLDivElement>,
    showToast: (String) -> Unit,
    persistSessionSnapshot: suspend (((IdentitySessionSnapshot?) -> IdentitySessionSnapshot)) -> Unit,
    onTopicPaneRequested: (Topic?) -> Unit,
    onPostPaneRequested: (QueryResult.Entry?) -> Unit,
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
    val snapshotRefreshVersionRef = useRef(snapshotRefreshVersion)
    val replyController =
        useIdentityConnectedViewReplyController(
            translator = translator,
            replyPublisher = replyPublisher,
            replySearchService = replySearchService,
            scope = scope,
            repliesContainerRef = repliesContainerRef,
            onPostPaneRequested = onPostPaneRequested,
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
        postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        if (currentTopic == null) {
            posts = emptyList()
            isLoadingPosts = false
            return true
        }
        setAppendingState(false)
        isLoadingPosts = true
        val results =
            runCatching { searchService.fetchFor(listOf(currentTopic)) }
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
        scope.launch { repository.applyFilters(sanitized) }
    }

    fun selectTopic(topic: Topic?) {
        if (topic == selectedTopic) {
            if (isMobile && topic != null) {
                replyController.clearReplies()
                onTopicPaneRequested(topic)
            }
            return
        }
        replyController.clearReplies()
        onTopicPaneRequested(topic)
        selectedTopic = topic
        scope.launch {
            persistSessionSnapshot { snapshot ->
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

    useEffect(snapshotRefreshVersion) {
        if (snapshotRefreshVersion == snapshotRefreshVersionRef.current) return@useEffect
        snapshotRefreshVersionRef.current = snapshotRefreshVersion
        val topicForRefresh = selectedTopic
        replyController.clearReplies()
        scope.launch {
            postCache.clearAll()
            replyCache.clearAll()
            val refreshed = refreshPosts(topicForRefresh)
            if (!refreshed && selectedTopic?.title == topicForRefresh?.title) {
                showToast(translator.translate("connected.composer.refresh.error"))
            }
        }
    }

    useEffect(*noContentDependencies) {
        scope.launch {
            val loadedTopics = repository.all()
            topics = loadedTopics
            topicFilters = extractTopicFilters(loadedTopics)
            val storedSelection = runCatching { sessionStore.load() }.getOrNull()?.selectedTopicTitle
            selectTopic(loadedTopics.firstOrNull { it.title == storedSelection } ?: loadedTopics.firstOrNull())
        }
    }
    val contentActions =
        buildIdentityConnectedViewContentActions(
            translator = translator,
            repository = repository,
            searchService = searchService,
            postCache = postCache,
            postPublisher = postPublisher,
            postDeletionService = postDeletionService,
            allPostsDeletionService = allPostsDeletionService,
            scope = scope,
            postsContainerRef = postsContainerRef,
            showToast = showToast,
            topics = { topics },
            selectedTopic = { selectedTopic },
            topicFilters = { topicFilters },
            posts = { posts },
            isLoadingPosts = { isLoadingPosts },
            isAppendingPosts = { isAppendingPostsRef.current == true },
            isPublishing = { isPublishing },
            isDeletingPost = { isDeletingPost },
            isDeletingAllPosts = { isDeletingAllPosts },
            setTopics = { topics = it },
            setPosts = { posts = it },
            selectTopic = ::selectTopic,
            persistFilters = ::persistFilters,
            refreshPosts = ::refreshPosts,
            setAppendingState = ::setAppendingState,
            setComposerOpen = { isComposerOpen = it },
            setComposerText = { composerText = it },
            composerText = { composerText },
            setPublishing = { isPublishing = it },
            setPublishError = { publishError = it },
            setDeleteCandidate = { deleteCandidate = it },
            setDeleteError = { deleteError = it },
            setDeletingPost = { isDeletingPost = it },
            setDeletingAllPosts = { isDeletingAllPosts = it },
            removePost = ::removePost,
            removePosts = ::removePosts,
        )

    return buildIdentityConnectedViewContentController(
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
        findTopicByTitle = { title -> findTopicByTitle(topics, title) },
        findPostById = { id -> findPostById(posts, id) },
    )
}
