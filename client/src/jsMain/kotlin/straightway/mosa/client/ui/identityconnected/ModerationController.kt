package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.browser.document
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlin.js.unsafeCast
import org.w3c.dom.events.Event
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult
import web.dom.Element
import web.html.HTMLButtonElement
import web.html.HTMLDivElement

internal fun useModerationController(
    dependencies: ModerationControllerDependencies,
): ModerationController {
    var isInboxOpen by useState(false)
    var isInboxLoading by useState(false)
    var isInboxAppending by useState(false)
    var inboxEntries by useState<List<AdminMessage>>(emptyList())
    var seenInboxMessageIds by useState<List<String>>(emptyList())
    var showSeenInboxEntries by useState(false)
    var hiddenSeenInboxMessageIds by useState<List<String>>(emptyList())
    var inboxError by useState<String?>(null)
    var inboxPage by useState(1)
    var inboxHasMore by useState(false)
    var inboxRefreshFromTime by useState(0L)
    var unreadInboxCount by useState(0)
    var isComplaintQueueOpen by useState(false)
    var isComplaintQueueLoading by useState(false)
    var isComplaintQueueAppending by useState(false)
    var complaintQueueEntries by useState<List<ComplaintQueueEntry>>(emptyList())
    var complaintQueueError by useState<String?>(null)
    var complaintQueuePage by useState(1)
    var complaintQueueHasMore by useState(false)
    var openComplaintCount by useState(0)
    var showProcessedComplaintEntries by useState(false)
    var complaintActionPostId by useState<String?>(null)
    var complaintComposerPostId by useState<String?>(null)
    var complaintComposerText by useState("")
    var complaintComposerError by useState<String?>(null)
    var isComplaintSubmitting by useState(false)
    var complaintDecisionEntryId by useState<String?>(null)
    var complaintDecisionTargetStatus by useState<PostComplaintState?>(null)
    var complaintDecisionText by useState("")
    var complaintDecisionError by useState<String?>(null)
    var isComplaintDecisionSubmitting by useState(false)
    val connectedIdentityGidRef = useRef(dependencies.connectedIdentityGid)
    val isInboxOpenRef = useRef(false)
    val isComplaintQueueOpenRef = useRef(false)
    val complaintQueueEntriesRef = useRef<List<ComplaintQueueEntry>>(emptyList())
    val inboxScrollRestoreRef = useRef<Double>()
    val inboxButtonRef = useRef<HTMLButtonElement>()
    val complaintQueueButtonRef = useRef<HTMLButtonElement>()
    val inboxPopoverRef = useRef<HTMLDivElement>()
    val complaintQueuePopoverRef = useRef<HTMLDivElement>()
    isInboxOpenRef.current = isInboxOpen
    isComplaintQueueOpenRef.current = isComplaintQueueOpen
    complaintQueueEntriesRef.current = complaintQueueEntries

    fun loadInboxStateSnapshot(): AdminMessageInboxStateSnapshot =
        loadModerationInboxStateSnapshot(
            dependencies.inboxStateStore,
            dependencies.podBaseProvider,
            dependencies.connectedIdentityGid,
        )

    fun persistInboxStateSnapshot(snapshot: AdminMessageInboxStateSnapshot) =
        persistModerationInboxStateSnapshot(
            dependencies.inboxStateStore,
            dependencies.podBaseProvider,
            dependencies.connectedIdentityGid,
            snapshot,
        )

    fun applyInboxPage(result: AdminMessageListResult, markAsSeen: Boolean, hiddenSeenMessageIds: List<String>) {
        inboxPage = result.page
        inboxHasMore = result.hasMore
        val nextSnapshot = loadInboxStateSnapshot().mergeFetchedEntries(result.entries, markAsSeen)
        persistInboxStateSnapshot(nextSnapshot)
        inboxEntries = nextSnapshot.entries
        seenInboxMessageIds = nextSnapshot.seenMessageIds
        hiddenSeenInboxMessageIds = hiddenSeenMessageIds
        unreadInboxCount = unreadModerationInboxCount(nextSnapshot)
    }

    fun refreshInbox(openAfterLoad: Boolean) {
        if (isInboxLoading || isInboxAppending) return
        val currentSnapshot = loadInboxStateSnapshot()
        val refreshFromTime = currentSnapshot.nextRefreshFromTime()
        val willBeOpen = openAfterLoad || isInboxOpenRef.current == true
        val hiddenSeenMessageIds =
            if (isInboxOpenRef.current == true) hiddenSeenInboxMessageIds else currentSnapshot.seenMessageIds
        isInboxOpen = willBeOpen
        isInboxLoading = true
        isInboxAppending = false
        inboxRefreshFromTime = refreshFromTime
        inboxError = null
        dependencies.context.async.scope.launch {
            val result =
                runCatching {
                    dependencies.adminMessageInboxService.fetch(page = 1, fromTime = refreshFromTime)
                }.getOrNull()
            isInboxLoading = false
            if (result == null) {
                inboxEntries = currentSnapshot.entries
                seenInboxMessageIds = currentSnapshot.seenMessageIds
                hiddenSeenInboxMessageIds = hiddenSeenMessageIds
                inboxError = dependencies.context.async.translator.translate("connected.messages.inbox.error")
            } else {
                applyInboxPage(result, willBeOpen, hiddenSeenMessageIds)
            }
        }
    }

    fun applyComplaintQueuePage(result: OpenPostComplaintsResult, append: Boolean) {
        val mergedEntries = mergeComplaintQueueEntries(complaintQueueEntriesRef.current ?: emptyList(), result, append)
        complaintQueueEntries = mergedEntries
        complaintQueuePage = result.page
        complaintQueueHasMore = result.hasMore
    }

    fun refreshComplaintCount(forceRefresh: Boolean = false) {
        if (!dependencies.showsConnectedAdminBadge) {
            openComplaintCount = 0
            return
        }
        dependencies.context.async.scope.launch {
            val result = runCatching { dependencies.adminPostComplaintService.countOpen(forceRefresh = forceRefresh) }.getOrNull()
            openComplaintCount = result?.count ?: 0
        }
    }

    val actions =
        buildModerationActions(
            dependencies =
                ModerationActionDependencies(
                    context = dependencies.context,
                    adminMessageInboxService = dependencies.adminMessageInboxService,
                    postComplaintService = dependencies.postComplaintService,
                    adminPostComplaintService = dependencies.adminPostComplaintService,
                    connectedIdentityGid = dependencies.connectedIdentityGid,
                ),
            inbox =
                ModerationInboxModel(
                    refreshInbox = ::refreshInbox,
                    isOpen = isInboxOpen,
                    isLoading = isInboxLoading,
                    isAppending = isInboxAppending,
                    hasMore = inboxHasMore,
                    page = inboxPage,
                    refreshFromTime = inboxRefreshFromTime,
                    hiddenSeenMessageIds = hiddenSeenInboxMessageIds,
                    setInboxOpen = { isInboxOpen = it },
                    setInboxError = { inboxError = it },
                    setInboxAppending = { isInboxAppending = it },
                    applyInboxPage = ::applyInboxPage,
                    rememberInboxScrollTop = { inboxScrollRestoreRef.current = inboxPopoverRef.current?.scrollTop },
                ),
            complaint =
                ModerationComplaintQueueModel(
                    isOpen = isComplaintQueueOpen,
                    isLoading = isComplaintQueueLoading,
                    isAppending = isComplaintQueueAppending,
                    hasMore = complaintQueueHasMore,
                    page = complaintQueuePage,
                    showProcessedEntries = showProcessedComplaintEntries,
                    entries = complaintQueueEntries,
                    actionPostId = complaintActionPostId,
                    composerPostId = complaintComposerPostId,
                    composerText = complaintComposerText,
                    isSubmitting = isComplaintSubmitting,
                    decisionEntryId = complaintDecisionEntryId,
                    decisionTargetStatus = complaintDecisionTargetStatus,
                    decisionText = complaintDecisionText,
                    isDecisionSubmitting = isComplaintDecisionSubmitting,
                    setComplaintQueueOpen = { isComplaintQueueOpen = it },
                    setComplaintQueueLoading = { isComplaintQueueLoading = it },
                    setComplaintQueueAppending = { isComplaintQueueAppending = it },
                    setComplaintQueueError = { complaintQueueError = it },
                    setComplaintQueuePage = { complaintQueuePage = it },
                    setComplaintQueueHasMore = { complaintQueueHasMore = it },
                    applyComplaintQueuePage = ::applyComplaintQueuePage,
                    refreshComplaintCount = ::refreshComplaintCount,
                    setShowProcessedComplaintEntries = { showProcessedComplaintEntries = it },
                    setComplaintActionPostId = { complaintActionPostId = it },
                    setComplaintComposerPostId = { complaintComposerPostId = it },
                    setComplaintComposerTextState = { complaintComposerText = it },
                    setComplaintComposerError = { complaintComposerError = it },
                    setComplaintSubmitting = { isComplaintSubmitting = it },
                    setComplaintDecisionEntryId = { complaintDecisionEntryId = it },
                    setComplaintDecisionTargetStatus = { complaintDecisionTargetStatus = it },
                    setComplaintDecisionTextState = { complaintDecisionText = it },
                    setComplaintDecisionError = { complaintDecisionError = it },
                    setComplaintDecisionSubmitting = { isComplaintDecisionSubmitting = it },
                    updateComplaintQueueEntries = { complaintQueueEntries = it },
                ),
        )

    useEffect(dependencies.connectedIdentityGid) {
        val storedSnapshot = loadInboxStateSnapshot()
        inboxEntries = storedSnapshot.entries
        seenInboxMessageIds = storedSnapshot.seenMessageIds
        hiddenSeenInboxMessageIds = storedSnapshot.seenMessageIds
        showSeenInboxEntries = false
        unreadInboxCount = unreadModerationInboxCount(storedSnapshot)
        inboxError = null
        val nextConnectedIdentityGid = dependencies.connectedIdentityGid
        if (nextConnectedIdentityGid == connectedIdentityGidRef.current) return@useEffect
        connectedIdentityGidRef.current = nextConnectedIdentityGid
        if (nextConnectedIdentityGid == null) {
            isInboxLoading = false
            isInboxOpen = false
            return@useEffect
        }
        refreshInbox(false)
    }

    useEffect(arrayOf(dependencies.showsConnectedAdminBadge, dependencies.connectedIdentityGid)) {
        refreshComplaintCount()
    }

    useEffect(arrayOf(inboxEntries, isInboxAppending)) {
        val scrollTop = inboxScrollRestoreRef.current ?: return@useEffect
        inboxPopoverRef.current?.scrollTop = scrollTop
        if (!isInboxAppending) inboxScrollRestoreRef.current = null
    }

    useEffect(*emptyArray<dynamic>()) {
        val inboxOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if (isInboxOpenRef.current != true) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedInboxButton = inboxButtonRef.current?.contains(target) == true
            val clickedInboxPopover = inboxPopoverRef.current?.contains(target) == true
            if (!clickedInboxButton && !clickedInboxPopover) {
                isInboxOpen = false
            }
        }
        document.addEventListener("mousedown", inboxOutsideClickListener)
        val complaintQueueOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if (isComplaintQueueOpenRef.current != true) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedComplaintQueueButton = complaintQueueButtonRef.current?.contains(target) == true
            val clickedComplaintQueuePopover = complaintQueuePopoverRef.current?.contains(target) == true
            if (!clickedComplaintQueueButton && !clickedComplaintQueuePopover) {
                isComplaintQueueOpen = false
            }
        }
        document.addEventListener("mousedown", complaintQueueOutsideClickListener)
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("mousedown", inboxOutsideClickListener)
            document.removeEventListener("mousedown", complaintQueueOutsideClickListener)
        }
    }

    return ModerationController(
        state =
            ModerationControllerState(
                isInboxOpen = isInboxOpen,
                isInboxLoading = isInboxLoading,
                isInboxAppending = isInboxAppending,
                inboxEntries = inboxEntries,
                showSeenInboxEntries = showSeenInboxEntries,
                hiddenSeenInboxMessageIds = hiddenSeenInboxMessageIds,
                inboxError = inboxError,
                inboxHasMore = inboxHasMore,
                seenInboxMessageIds = seenInboxMessageIds,
                unreadInboxCount = unreadInboxCount,
                isComplaintQueueOpen = isComplaintQueueOpen,
                isComplaintQueueLoading = isComplaintQueueLoading,
                isComplaintQueueAppending = isComplaintQueueAppending,
                complaintQueueEntries = complaintQueueEntries,
                complaintQueueError = complaintQueueError,
                complaintQueueHasMore = complaintQueueHasMore,
                showProcessedComplaintEntries = showProcessedComplaintEntries,
                openComplaintCount = openComplaintCount,
                complaintActionPostId = complaintActionPostId,
                complaintComposerPostId = complaintComposerPostId,
                complaintComposerText = complaintComposerText,
                complaintComposerError = complaintComposerError,
                isComplaintSubmitting = isComplaintSubmitting,
                complaintDecisionEntryId = complaintDecisionEntryId,
                complaintDecisionTargetStatus = complaintDecisionTargetStatus,
                complaintDecisionText = complaintDecisionText,
                complaintDecisionError = complaintDecisionError,
                isComplaintDecisionSubmitting = isComplaintDecisionSubmitting,
            ),
        refs =
            ModerationControllerRefs(
                inboxButtonRef = inboxButtonRef,
                complaintQueueButtonRef = complaintQueueButtonRef,
                inboxPopoverRef = inboxPopoverRef,
                complaintQueuePopoverRef = complaintQueuePopoverRef,
            ),
        actions =
            ModerationControllerActions(
                toggleSeenInboxVisibility = { showSeenInboxEntries = !showSeenInboxEntries },
                loadInbox = actions.loadInbox,
                closeInbox = actions.closeInbox,
                loadMoreInbox = actions.loadMoreInbox,
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
    )
}
