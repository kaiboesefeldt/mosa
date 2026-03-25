package straightway.mosa.client.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.QueryResult
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintAvailability
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessageListResult

internal data class IdentityConnectedViewModerationActions(
    val loadInbox: () -> Unit,
    val closeInbox: () -> Unit,
    val loadMoreInbox: () -> Unit,
    val loadComplaintQueue: () -> Unit,
    val closeComplaintQueue: () -> Unit,
    val toggleProcessedComplaintVisibility: () -> Unit,
    val loadMoreComplaintQueue: () -> Unit,
    val requestPostComplaint: (QueryResult.Entry) -> Unit,
    val setComplaintComposerText: (String) -> Unit,
    val closeComplaintComposer: () -> Unit,
    val submitPostComplaint: () -> Unit,
    val startComplaintDecision: (ComplaintQueueEntry, PostComplaintState) -> Unit,
    val setComplaintDecisionText: (String) -> Unit,
    val closeComplaintDecisionComposer: () -> Unit,
    val submitComplaintDecision: () -> Unit,
)

internal fun buildIdentityConnectedViewModerationActions(
    translator: Translator,
    adminMessageInboxService: AdminMessageInboxService,
    postComplaintService: PostComplaintService,
    adminPostComplaintService: AdminPostComplaintService,
    scope: CoroutineScope,
    connectedIdentityGid: () -> String?,
    showToast: (String) -> Unit,
    refreshInbox: (Boolean) -> Unit,
    isInboxOpen: () -> Boolean,
    isInboxLoading: () -> Boolean,
    isInboxAppending: () -> Boolean,
    inboxHasMore: () -> Boolean,
    inboxPage: () -> Int,
    inboxRefreshFromTime: () -> Long,
    hiddenSeenInboxMessageIds: () -> List<String>,
    setInboxOpen: (Boolean) -> Unit,
    setInboxError: (String?) -> Unit,
    setInboxAppending: (Boolean) -> Unit,
    applyInboxPage: (AdminMessageListResult, Boolean, List<String>) -> Unit,
    rememberInboxScrollTop: () -> Unit,
    isComplaintQueueOpen: () -> Boolean,
    isComplaintQueueLoading: () -> Boolean,
    isComplaintQueueAppending: () -> Boolean,
    complaintQueueHasMore: () -> Boolean,
    complaintQueuePage: () -> Int,
    showProcessedComplaintEntries: () -> Boolean,
    complaintQueueEntries: () -> List<ComplaintQueueEntry>,
    complaintActionPostId: () -> String?,
    complaintComposerPostId: () -> String?,
    complaintComposerText: () -> String,
    isComplaintSubmitting: () -> Boolean,
    complaintDecisionEntryId: () -> String?,
    complaintDecisionTargetStatus: () -> PostComplaintState?,
    complaintDecisionText: () -> String,
    isComplaintDecisionSubmitting: () -> Boolean,
    setComplaintQueueOpen: (Boolean) -> Unit,
    setComplaintQueueLoading: (Boolean) -> Unit,
    setComplaintQueueAppending: (Boolean) -> Unit,
    setComplaintQueueError: (String?) -> Unit,
    setComplaintQueuePage: (Int) -> Unit,
    setComplaintQueueHasMore: (Boolean) -> Unit,
    applyComplaintQueuePage: (OpenPostComplaintsResult, Boolean) -> Unit,
    refreshComplaintCount: (Boolean) -> Unit,
    setShowProcessedComplaintEntries: (Boolean) -> Unit,
    setComplaintActionPostId: (String?) -> Unit,
    setComplaintComposerPostId: (String?) -> Unit,
    setComplaintComposerTextState: (String) -> Unit,
    setComplaintComposerError: (String?) -> Unit,
    setComplaintSubmitting: (Boolean) -> Unit,
    setComplaintDecisionEntryId: (String?) -> Unit,
    setComplaintDecisionTargetStatus: (PostComplaintState?) -> Unit,
    setComplaintDecisionTextState: (String) -> Unit,
    setComplaintDecisionError: (String?) -> Unit,
    setComplaintDecisionSubmitting: (Boolean) -> Unit,
    updateComplaintQueueEntries: (List<ComplaintQueueEntry>) -> Unit,
): IdentityConnectedViewModerationActions {
    fun refreshComplaintQueue(
        openAfterLoad: Boolean,
        includeProcessed: Boolean = showProcessedComplaintEntries(),
    ) {
        if (isComplaintQueueLoading() || isComplaintQueueAppending()) return
        setComplaintQueueOpen(openAfterLoad || isComplaintQueueOpen())
        setComplaintQueueLoading(true)
        setComplaintQueueAppending(false)
        setComplaintQueueError(null)
        scope.launch {
            val result = runCatching { adminPostComplaintService.listOpen(page = 1, includeProcessed = includeProcessed) }.getOrNull()
            setComplaintQueueLoading(false)
            if (result == null) {
                updateComplaintQueueEntries(emptyList())
                setComplaintQueuePage(1)
                setComplaintQueueHasMore(false)
                setComplaintQueueError(translator.translate("connected.complaints.admin.error"))
            } else {
                applyComplaintQueuePage(result, false)
            }
            refreshComplaintCount(true)
        }
    }

    return IdentityConnectedViewModerationActions(
        loadInbox = { refreshInbox(true) },
        closeInbox = { setInboxOpen(false) },
        loadMoreInbox = {
            if (!isInboxOpen() || isInboxLoading() || isInboxAppending() || !inboxHasMore()) {
                return@IdentityConnectedViewModerationActions
            }
            rememberInboxScrollTop()
            setInboxAppending(true)
            setInboxError(null)
            val nextPage = inboxPage() + 1
            scope.launch {
                val result =
                    runCatching { adminMessageInboxService.fetch(page = nextPage, fromTime = inboxRefreshFromTime()) }.getOrNull()
                setInboxAppending(false)
                if (result == null) {
                    setInboxError(translator.translate("connected.messages.inbox.error"))
                } else {
                    applyInboxPage(result, true, hiddenSeenInboxMessageIds())
                }
            }
        },
        loadComplaintQueue = { refreshComplaintQueue(true) },
        closeComplaintQueue = { setComplaintQueueOpen(false) },
        toggleProcessedComplaintVisibility = {
            val willShowProcessedEntries = !showProcessedComplaintEntries()
            setShowProcessedComplaintEntries(willShowProcessedEntries)
            if (isComplaintQueueOpen()) {
                refreshComplaintQueue(openAfterLoad = true, includeProcessed = willShowProcessedEntries)
            }
        },
        loadMoreComplaintQueue = {
            if (!isComplaintQueueOpen() || isComplaintQueueLoading() || isComplaintQueueAppending() || !complaintQueueHasMore()) {
                return@IdentityConnectedViewModerationActions
            }
            setComplaintQueueAppending(true)
            setComplaintQueueError(null)
            val nextPage = complaintQueuePage() + 1
            val includeProcessed = showProcessedComplaintEntries()
            scope.launch {
                val result = runCatching { adminPostComplaintService.listOpen(page = nextPage, includeProcessed = includeProcessed) }.getOrNull()
                setComplaintQueueAppending(false)
                if (result == null) {
                    setComplaintQueueError(translator.translate("connected.complaints.admin.error"))
                } else {
                    applyComplaintQueuePage(result, true)
                }
            }
        },
        requestPostComplaint = { entry ->
            if (complaintActionPostId() != null || complaintComposerPostId() != null || connectedIdentityGid() == null) {
                return@IdentityConnectedViewModerationActions
            }
            if (entry.authorGID == connectedIdentityGid()) return@IdentityConnectedViewModerationActions
            setComplaintActionPostId(entry.id)
            scope.launch {
                try {
                    when (postComplaintService.status(entry.id)?.availability) {
                        PostComplaintAvailability.OPEN -> showToast(translator.translate("connected.complaints.post.open"))
                        PostComplaintAvailability.BLOCKED -> showToast(translator.translate("connected.complaints.post.blocked"))
                        PostComplaintAvailability.AVAILABLE -> {
                            setComplaintComposerPostId(entry.id)
                            setComplaintComposerTextState("")
                            setComplaintComposerError(null)
                        }
                        null -> showToast(translator.translate("connected.complaints.post.error"))
                    }
                } finally {
                    setComplaintActionPostId(null)
                }
            }
        },
        setComplaintComposerText = { value ->
            setComplaintComposerTextState(value)
            setComplaintComposerError(null)
        },
        closeComplaintComposer = {
            if (isComplaintSubmitting()) return@IdentityConnectedViewModerationActions
            setComplaintComposerPostId(null)
            setComplaintComposerTextState("")
            setComplaintComposerError(null)
        },
        submitPostComplaint = {
            val postId = complaintComposerPostId() ?: return@IdentityConnectedViewModerationActions
            val reason = complaintComposerText().trim()
            if (reason.isEmpty()) {
                setComplaintComposerError(translator.translate("connected.complaints.post.reasonRequired"))
                return@IdentityConnectedViewModerationActions
            }
            if (500 < reason.length) {
                setComplaintComposerError(translator.translate("connected.complaints.post.reasonTooLong"))
                return@IdentityConnectedViewModerationActions
            }
            setComplaintSubmitting(true)
            setComplaintComposerError(null)
            scope.launch {
                val created = runCatching { postComplaintService.submit(postId, reason) }.getOrDefault(false)
                setComplaintSubmitting(false)
                if (created) {
                    setComplaintComposerPostId(null)
                    setComplaintComposerTextState("")
                    showToast(translator.translate("connected.complaints.post.sent"))
                    refreshComplaintCount(true)
                } else {
                    setComplaintComposerError(translator.translate("connected.complaints.post.error"))
                }
            }
        },
        startComplaintDecision = { entry, targetStatus ->
            if (!entry.canTransitionTo(targetStatus) || isComplaintDecisionSubmitting()) return@IdentityConnectedViewModerationActions
            setComplaintDecisionEntryId(entry.complaintId)
            setComplaintDecisionTargetStatus(targetStatus)
            setComplaintDecisionTextState("")
            setComplaintDecisionError(null)
        },
        setComplaintDecisionText = { value ->
            setComplaintDecisionTextState(value)
            setComplaintDecisionError(null)
        },
        closeComplaintDecisionComposer = {
            if (isComplaintDecisionSubmitting()) return@IdentityConnectedViewModerationActions
            setComplaintDecisionEntryId(null)
            setComplaintDecisionTargetStatus(null)
            setComplaintDecisionTextState("")
            setComplaintDecisionError(null)
        },
        submitComplaintDecision = {
            val complaintId = complaintDecisionEntryId() ?: return@IdentityConnectedViewModerationActions
            val targetStatus = complaintDecisionTargetStatus() ?: return@IdentityConnectedViewModerationActions
            val entry = complaintQueueEntries().firstOrNull { it.complaintId == complaintId } ?: return@IdentityConnectedViewModerationActions
            val feedback = complaintDecisionText().trim()
            if (feedback.isEmpty()) {
                setComplaintDecisionError(translator.translate("connected.complaints.admin.feedbackRequired"))
                return@IdentityConnectedViewModerationActions
            }
            if (1000 < feedback.length) {
                setComplaintDecisionError(translator.translate("connected.complaints.admin.feedbackTooLong"))
                return@IdentityConnectedViewModerationActions
            }
            setComplaintDecisionSubmitting(true)
            setComplaintDecisionError(null)
            scope.launch {
                val success =
                    adminPostComplaintService.decide(
                        complaintId = entry.complaintId,
                        postId = entry.complaint.postId,
                        targetStatus = targetStatus,
                        feedback = feedback,
                    )
                setComplaintDecisionSubmitting(false)
                if (success) {
                    updateComplaintQueueEntries(
                        complaintQueueEntries().map { currentEntry ->
                            if (currentEntry.complaintId == complaintId) currentEntry.copy(state = targetStatus) else currentEntry
                        },
                    )
                    setComplaintDecisionEntryId(null)
                    setComplaintDecisionTargetStatus(null)
                    setComplaintDecisionTextState("")
                    showToast(translator.translate("connected.complaints.admin.decided"))
                    refreshComplaintQueue(openAfterLoad = complaintQueueEntries().size > 1)
                    refreshInbox(false)
                } else {
                    setComplaintDecisionError(translator.translate("connected.complaints.admin.error"))
                }
            }
        },
    )
}
