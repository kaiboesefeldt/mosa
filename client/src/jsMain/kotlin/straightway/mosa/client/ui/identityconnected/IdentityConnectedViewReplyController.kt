package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.coroutines.launch
import react.useRef
import react.useState
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.content.QueryResult

internal data class IdentityConnectedViewReplyControllerState(
    val selectedPost: QueryResult.Entry?,
    val replies: ThreadReplies?,
    val isLoadingReplies: Boolean,
    val isReplyComposerOpen: Boolean,
    val replyComposerText: String,
    val isReplyPublishing: Boolean,
    val replyPublishError: String?,
)

internal data class IdentityConnectedViewReplyControllerActions(
    val setReplyComposerText: (String) -> Unit,
    val openReplyComposer: () -> Unit,
    val closeReplyComposer: () -> Unit,
    val sendReply: () -> Unit,
    val loadMoreReplies: () -> Unit,
    val selectPost: (QueryResult.Entry) -> Unit,
    val clearReplies: () -> Unit,
    val dropDeletedPosts: (Collection<String>) -> Unit,
)

internal data class IdentityConnectedViewReplyController(
    val state: IdentityConnectedViewReplyControllerState,
    val actions: IdentityConnectedViewReplyControllerActions,
)

internal fun useIdentityConnectedViewReplyController(
    dependencies: IdentityConnectedViewReplyControllerDependencies,
): IdentityConnectedViewReplyController {
    var selectedPost by useState<QueryResult.Entry?>(null)
    var replies by useState<ThreadReplies?>(null)
    var isLoadingReplies by useState(false)
    var isReplyComposerOpen by useState(false)
    var replyComposerText by useState("")
    var isReplyPublishing by useState(false)
    var replyPublishError by useState<String?>(null)
    val repliesRequestVersion = useRef(0)
    val isAppendingRepliesRef = useRef(false)

    fun resetReplyComposer() {
        isReplyComposerOpen = false
        replyComposerText = ""
        replyPublishError = null
        isReplyPublishing = false
    }

    fun clearReplies() {
        dependencies.repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        selectedPost = null
        replies = null
        isLoadingReplies = false
        isAppendingRepliesRef.current = false
        resetReplyComposer()
    }

    fun dropDeletedPosts(postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        val idSet = postIds.toSet()
        if (selectedPost?.id in idSet) {
            selectedPost = null
            replies = null
        } else {
            replies = removeRepliesFromStateEntries(replies, idSet)
        }
    }

    return IdentityConnectedViewReplyController(
        state =
            IdentityConnectedViewReplyControllerState(
                selectedPost = selectedPost,
                replies = replies,
                isLoadingReplies = isLoadingReplies,
                isReplyComposerOpen = isReplyComposerOpen,
                replyComposerText = replyComposerText,
                isReplyPublishing = isReplyPublishing,
                replyPublishError = replyPublishError,
            ),
        actions =
            IdentityConnectedViewReplyControllerActions(
                setReplyComposerText = { replyComposerText = it },
                openReplyComposer = {
                    if (selectedPost != null && !isReplyPublishing) {
                        replyPublishError = null
                        isReplyComposerOpen = true
                    }
                },
                closeReplyComposer = {
                    if (!isReplyPublishing) {
                        replyPublishError = null
                        isReplyComposerOpen = false
                    }
                },
                sendReply = {
                    val targetPost = selectedPost
                    val text = replyComposerText.trim()
                    if (!isReplyPublishing && targetPost != null && text.isNotEmpty()) {
                        val rootId = rootIdFor(targetPost)
                        isReplyPublishing = true
                        replyPublishError = null
                        dependencies.context.scope.launch {
                            val published = dependencies.replyPublisher.publish(text, rootId)
                            if (!published) {
                                replyPublishError = dependencies.context.translator.translate("connected.replies.compose.error")
                                isReplyPublishing = false
                                return@launch
                            }
                            replyComposerText = ""
                            isReplyComposerOpen = false
                            if (selectedPost?.let(::rootIdFor) != rootId) {
                                isReplyPublishing = false
                                return@launch
                            }
                            val nextVersion = (repliesRequestVersion.current ?: 0) + 1
                            repliesRequestVersion.current = nextVersion
                            isLoadingReplies = true
                            dependencies.repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
                            val refreshed =
                                runCatching { dependencies.replySearchService.fetchFor(rootId) }.getOrElse {
                                    if (repliesRequestVersion.current == nextVersion) isLoadingReplies = false
                                    null
                                }
                            if (repliesRequestVersion.current == nextVersion && refreshed != null) replies = refreshed
                            if (repliesRequestVersion.current == nextVersion) isLoadingReplies = false
                            isReplyPublishing = false
                        }
                    }
                },
                loadMoreReplies = {
                    val rootId = replies?.rootId
                    if (rootId != null && !isLoadingReplies && isAppendingRepliesRef.current != true) {
                        dependencies.repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
                        isAppendingRepliesRef.current = true
                        dependencies.context.scope.launch {
                            try {
                                dependencies.replySearchService.fetchMore(rootId)?.let { replies = it }
                            } finally {
                                isAppendingRepliesRef.current = false
                            }
                        }
                    }
                },
                selectPost = { entry ->
                    resetReplyComposer()
                    val rootId = rootIdFor(entry)
                    val nextVersion = (repliesRequestVersion.current ?: 0) + 1
                    repliesRequestVersion.current = nextVersion
                    selectedPost = entry
                    dependencies.onPostPaneRequested(entry)
                    replies = null
                    dependencies.repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
                    isAppendingRepliesRef.current = false
                    isLoadingReplies = true
                    dependencies.context.scope.launch {
                        val result =
                            runCatching { dependencies.replySearchService.fetchFor(rootId) }.getOrElse {
                                if (repliesRequestVersion.current == nextVersion) {
                                    replies = null
                                    isLoadingReplies = false
                                }
                                return@launch
                            }
                        if (repliesRequestVersion.current != nextVersion) return@launch
                        replies = result
                        isLoadingReplies = false
                    }
                },
                clearReplies = ::clearReplies,
                dropDeletedPosts = ::dropDeletedPosts,
            ),
    )
}
