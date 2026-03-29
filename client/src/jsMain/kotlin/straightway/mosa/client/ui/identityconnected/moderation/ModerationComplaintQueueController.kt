package straightway.mosa.client.ui.identityconnected.moderation

import kotlinx.coroutines.launch
import react.RefObject
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.ui.identityconnected.common.ConnectedViewFeedbackContext
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState
import web.html.HTMLButtonElement
import web.html.HTMLDivElement

internal data class ModerationComplaintQueueController(
    val state: ModerationComplaintQueueControllerState,
    val actions: ModerationComplaintQueueControllerActions,
    val refs: ModerationComplaintQueueControllerRefs,
)

internal data class ModerationComplaintQueueControllerState(
    val isOpen: Boolean,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val entries: List<ComplaintQueueEntry>,
    val error: String?,
    val page: Int,
    val hasMore: Boolean,
    val openCount: Int,
    val showProcessedEntries: Boolean,
    val actionPostId: String?,
    val composerPostId: String?,
    val composerText: String,
    val composerError: String?,
    val isSubmitting: Boolean,
    val decisionEntryId: String?,
    val decisionTargetStatus: PostComplaintState?,
    val decisionText: String,
    val decisionError: String?,
    val isDecisionSubmitting: Boolean,
)

internal data class ModerationComplaintQueueControllerActions(
    val loadComplaintQueue: () -> Unit,
    val closeComplaintQueue: () -> Unit,
    val toggleProcessedComplaintVisibility: () -> Unit,
    val loadMoreComplaintQueue: () -> Unit,
    val requestPostComplaint: (straightway.mosa.content.QueryResult.Entry) -> Unit,
    val setComplaintComposerText: (String) -> Unit,
    val closeComplaintComposer: () -> Unit,
    val submitPostComplaint: () -> Unit,
    val startComplaintDecision: (ComplaintQueueEntry, PostComplaintState) -> Unit,
    val setComplaintDecisionText: (String) -> Unit,
    val closeComplaintDecisionComposer: () -> Unit,
    val submitComplaintDecision: () -> Unit,
)

internal data class ModerationComplaintQueueControllerRefs(
    val isOpenRef: RefObject<Boolean>,
    val entriesRef: RefObject<List<ComplaintQueueEntry>>,
    val buttonRef: RefObject<HTMLButtonElement>,
    val popoverRef: RefObject<HTMLDivElement>,
)

internal data class ModerationComplaintQueueControllerDependencies(
    val context: ConnectedViewFeedbackContext,
    val postComplaintService: PostComplaintService,
    val adminPostComplaintService: AdminPostComplaintService,
    val connectedIdentityGid: String?,
    val showsConnectedAdminBadge: Boolean,
)

internal fun useModerationComplaintQueueController(
    dependencies: ModerationComplaintQueueControllerDependencies,
    refreshInbox: (Boolean) -> Unit,
): ModerationComplaintQueueController {
    var isOpen by useState(false)
    var isLoading by useState(false)
    var isAppending by useState(false)
    var entries by useState<List<ComplaintQueueEntry>>(emptyList())
    var error by useState<String?>(null)
    var page by useState(1)
    var hasMore by useState(false)
    var openCount by useState(0)
    var showProcessedEntries by useState(false)
    var actionPostId by useState<String?>(null)
    var composerPostId by useState<String?>(null)
    var composerText by useState("")
    var composerError by useState<String?>(null)
    var isSubmitting by useState(false)
    var decisionEntryId by useState<String?>(null)
    var decisionTargetStatus by useState<PostComplaintState?>(null)
    var decisionText by useState("")
    var decisionError by useState<String?>(null)
    var isDecisionSubmitting by useState(false)
    val isOpenRef = useRef(false)
    val entriesRef = useRef<List<ComplaintQueueEntry>>(emptyList())
    val buttonRef = useRef<HTMLButtonElement>()
    val popoverRef = useRef<HTMLDivElement>()
    isOpenRef.current = isOpen
    entriesRef.current = entries

    fun applyComplaintQueuePage(result: OpenPostComplaintsResult, append: Boolean) {
        entries = mergeComplaintQueueEntries(entriesRef.current ?: emptyList(), result, append)
        page = result.page
        hasMore = result.hasMore
    }

    fun refreshComplaintCount(forceRefresh: Boolean = false) {
        if (!dependencies.showsConnectedAdminBadge) {
            openCount = 0
            return
        }
        dependencies.context.async.scope.launch {
            val result = runCatching { dependencies.adminPostComplaintService.countOpen(forceRefresh = forceRefresh) }.getOrNull()
            openCount = result?.count ?: 0
        }
    }

    val actions =
        buildModerationComplaintQueueActions(
            dependencies =
                ModerationComplaintQueueActionDependencies(
                    context = dependencies.context,
                    postComplaintService = dependencies.postComplaintService,
                    adminPostComplaintService = dependencies.adminPostComplaintService,
                    connectedIdentityGid = dependencies.connectedIdentityGid,
                ),
            inboxRefreshInbox = refreshInbox,
            complaint =
                ModerationComplaintQueueModel(
                    isOpen = isOpen,
                    isLoading = isLoading,
                    isAppending = isAppending,
                    hasMore = hasMore,
                    page = page,
                    showProcessedEntries = showProcessedEntries,
                    entries = entries,
                    actionPostId = actionPostId,
                    composerPostId = composerPostId,
                    composerText = composerText,
                    isSubmitting = isSubmitting,
                    decisionEntryId = decisionEntryId,
                    decisionTargetStatus = decisionTargetStatus,
                    decisionText = decisionText,
                    isDecisionSubmitting = isDecisionSubmitting,
                    setComplaintQueueOpen = { isOpen = it },
                    setComplaintQueueLoading = { isLoading = it },
                    setComplaintQueueAppending = { isAppending = it },
                    setComplaintQueueError = { error = it },
                    setComplaintQueuePage = { page = it },
                    setComplaintQueueHasMore = { hasMore = it },
                    applyComplaintQueuePage = ::applyComplaintQueuePage,
                    refreshComplaintCount = ::refreshComplaintCount,
                    setShowProcessedComplaintEntries = { showProcessedEntries = it },
                    setComplaintActionPostId = { actionPostId = it },
                    setComplaintComposerPostId = { composerPostId = it },
                    setComplaintComposerTextState = { composerText = it },
                    setComplaintComposerError = { composerError = it },
                    setComplaintSubmitting = { isSubmitting = it },
                    setComplaintDecisionEntryId = { decisionEntryId = it },
                    setComplaintDecisionTargetStatus = { decisionTargetStatus = it },
                    setComplaintDecisionTextState = { decisionText = it },
                    setComplaintDecisionError = { decisionError = it },
                    setComplaintDecisionSubmitting = { isDecisionSubmitting = it },
                    updateComplaintQueueEntries = { entries = it },
                ),
        )

    useEffect(arrayOf(dependencies.showsConnectedAdminBadge, dependencies.connectedIdentityGid)) {
        refreshComplaintCount()
    }

    return ModerationComplaintQueueController(
        state =
            ModerationComplaintQueueControllerState(
                isOpen = isOpen,
                isLoading = isLoading,
                isAppending = isAppending,
                entries = entries,
                error = error,
                page = page,
                hasMore = hasMore,
                openCount = openCount,
                showProcessedEntries = showProcessedEntries,
                actionPostId = actionPostId,
                composerPostId = composerPostId,
                composerText = composerText,
                composerError = composerError,
                isSubmitting = isSubmitting,
                decisionEntryId = decisionEntryId,
                decisionTargetStatus = decisionTargetStatus,
                decisionText = decisionText,
                decisionError = decisionError,
                isDecisionSubmitting = isDecisionSubmitting,
            ),
        actions =
            ModerationComplaintQueueControllerActions(
                loadComplaintQueue = actions.loadComplaintQueue,
                closeComplaintQueue = actions.closeComplaintQueue,
                toggleProcessedComplaintVisibility = actions.toggleProcessedComplaintVisibility,
                loadMoreComplaintQueue = actions.loadMoreComplaintQueue,
                requestPostComplaint = actions.requestPostComplaint,
                setComplaintComposerText = actions.setComplaintComposerText,
                closeComplaintComposer = actions.closeComplaintComposer,
                submitPostComplaint = actions.submitPostComplaint,
                startComplaintDecision = actions.startComplaintDecision,
                setComplaintDecisionText = actions.setComplaintDecisionText,
                closeComplaintDecisionComposer = actions.closeComplaintDecisionComposer,
                submitComplaintDecision = actions.submitComplaintDecision,
            ),
        refs =
            ModerationComplaintQueueControllerRefs(
                isOpenRef = isOpenRef,
                entriesRef = entriesRef,
                buttonRef = buttonRef,
                popoverRef = popoverRef,
            ),
    )
}
