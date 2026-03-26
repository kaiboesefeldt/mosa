package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.coroutines.launch
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
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
    dependencies: IdentityConnectedViewModerationActionDependencies,
    inbox: IdentityConnectedViewModerationInboxState,
    complaint: IdentityConnectedViewModerationComplaintState,
): IdentityConnectedViewModerationActions {
    fun refreshComplaintQueue(
        openAfterLoad: Boolean,
        includeProcessed: Boolean = complaint.showProcessedComplaintEntries(),
    ) {
        if (complaint.isComplaintQueueLoading() || complaint.isComplaintQueueAppending()) return
        complaint.setComplaintQueueOpen(openAfterLoad || complaint.isComplaintQueueOpen())
        complaint.setComplaintQueueLoading(true)
        complaint.setComplaintQueueAppending(false)
        complaint.setComplaintQueueError(null)
        dependencies.context.async.scope.launch {
            val result =
                runCatching {
                    dependencies.adminPostComplaintService.listOpen(page = 1, includeProcessed = includeProcessed)
                }.getOrNull()
            complaint.setComplaintQueueLoading(false)
            if (result == null) {
                complaint.updateComplaintQueueEntries(emptyList())
                complaint.setComplaintQueuePage(1)
                complaint.setComplaintQueueHasMore(false)
                complaint.setComplaintQueueError(dependencies.context.async.translator.translate("connected.complaints.admin.error"))
            } else {
                complaint.applyComplaintQueuePage(result, false)
            }
            complaint.refreshComplaintCount(true)
        }
    }

    return IdentityConnectedViewModerationActions(
        loadInbox = { inbox.refreshInbox(true) },
        closeInbox = { inbox.setInboxOpen(false) },
        loadMoreInbox = {
            if (!inbox.isInboxOpen() || inbox.isInboxLoading() || inbox.isInboxAppending() || !inbox.inboxHasMore()) {
                return@IdentityConnectedViewModerationActions
            }
            inbox.rememberInboxScrollTop()
            inbox.setInboxAppending(true)
            inbox.setInboxError(null)
            val nextPage = inbox.inboxPage() + 1
            dependencies.context.async.scope.launch {
                val result =
                    runCatching {
                        dependencies.adminMessageInboxService.fetch(page = nextPage, fromTime = inbox.inboxRefreshFromTime())
                    }.getOrNull()
                inbox.setInboxAppending(false)
                if (result == null) {
                    inbox.setInboxError(dependencies.context.async.translator.translate("connected.messages.inbox.error"))
                } else {
                    inbox.applyInboxPage(result, true, inbox.hiddenSeenInboxMessageIds())
                }
            }
        },
        loadComplaintQueue = { refreshComplaintQueue(true) },
        closeComplaintQueue = { complaint.setComplaintQueueOpen(false) },
        toggleProcessedComplaintVisibility = {
            val willShowProcessedEntries = !complaint.showProcessedComplaintEntries()
            complaint.setShowProcessedComplaintEntries(willShowProcessedEntries)
            if (complaint.isComplaintQueueOpen()) {
                refreshComplaintQueue(openAfterLoad = true, includeProcessed = willShowProcessedEntries)
            }
        },
        loadMoreComplaintQueue = {
            if (
                !complaint.isComplaintQueueOpen() ||
                complaint.isComplaintQueueLoading() ||
                complaint.isComplaintQueueAppending() ||
                !complaint.complaintQueueHasMore()
            ) {
                return@IdentityConnectedViewModerationActions
            }
            complaint.setComplaintQueueAppending(true)
            complaint.setComplaintQueueError(null)
            val nextPage = complaint.complaintQueuePage() + 1
            val includeProcessed = complaint.showProcessedComplaintEntries()
            dependencies.context.async.scope.launch {
                val result =
                    runCatching {
                        dependencies.adminPostComplaintService.listOpen(page = nextPage, includeProcessed = includeProcessed)
                    }.getOrNull()
                complaint.setComplaintQueueAppending(false)
                if (result == null) {
                    complaint.setComplaintQueueError(dependencies.context.async.translator.translate("connected.complaints.admin.error"))
                } else {
                    complaint.applyComplaintQueuePage(result, true)
                }
            }
        },
        requestPostComplaint = { entry ->
            if (
                complaint.complaintActionPostId() != null ||
                complaint.complaintComposerPostId() != null ||
                dependencies.connectedIdentityGid() == null
            ) {
                return@IdentityConnectedViewModerationActions
            }
            if (entry.authorGID == dependencies.connectedIdentityGid()) return@IdentityConnectedViewModerationActions
            complaint.setComplaintActionPostId(entry.id)
            dependencies.context.async.scope.launch {
                try {
                    when (dependencies.postComplaintService.status(entry.id)?.availability) {
                        PostComplaintAvailability.OPEN ->
                            dependencies.context.showToast(
                                dependencies.context.async.translator.translate("connected.complaints.post.open"),
                            )
                        PostComplaintAvailability.BLOCKED ->
                            dependencies.context.showToast(
                                dependencies.context.async.translator.translate("connected.complaints.post.blocked"),
                            )
                        PostComplaintAvailability.AVAILABLE -> {
                            complaint.setComplaintComposerPostId(entry.id)
                            complaint.setComplaintComposerTextState("")
                            complaint.setComplaintComposerError(null)
                        }
                        null ->
                            dependencies.context.showToast(
                                dependencies.context.async.translator.translate("connected.complaints.post.error"),
                            )
                    }
                } finally {
                    complaint.setComplaintActionPostId(null)
                }
            }
        },
        setComplaintComposerText = { value ->
            complaint.setComplaintComposerTextState(value)
            complaint.setComplaintComposerError(null)
        },
        closeComplaintComposer = {
            if (complaint.isComplaintSubmitting()) return@IdentityConnectedViewModerationActions
            complaint.setComplaintComposerPostId(null)
            complaint.setComplaintComposerTextState("")
            complaint.setComplaintComposerError(null)
        },
        submitPostComplaint = {
            val postId = complaint.complaintComposerPostId() ?: return@IdentityConnectedViewModerationActions
            val reason = complaint.complaintComposerText().trim()
            if (reason.isEmpty()) {
                complaint.setComplaintComposerError(
                    dependencies.context.async.translator.translate("connected.complaints.post.reasonRequired"),
                )
                return@IdentityConnectedViewModerationActions
            }
            if (500 < reason.length) {
                complaint.setComplaintComposerError(
                    dependencies.context.async.translator.translate("connected.complaints.post.reasonTooLong"),
                )
                return@IdentityConnectedViewModerationActions
            }
            complaint.setComplaintSubmitting(true)
            complaint.setComplaintComposerError(null)
            dependencies.context.async.scope.launch {
                val created = runCatching { dependencies.postComplaintService.submit(postId, reason) }.getOrDefault(false)
                complaint.setComplaintSubmitting(false)
                if (created) {
                    complaint.setComplaintComposerPostId(null)
                    complaint.setComplaintComposerTextState("")
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.complaints.post.sent"),
                    )
                    complaint.refreshComplaintCount(true)
                } else {
                    complaint.setComplaintComposerError(
                        dependencies.context.async.translator.translate("connected.complaints.post.error"),
                    )
                }
            }
        },
        startComplaintDecision = { entry, targetStatus ->
            if (!entry.canTransitionTo(targetStatus) || complaint.isComplaintDecisionSubmitting()) {
                return@IdentityConnectedViewModerationActions
            }
            complaint.setComplaintDecisionEntryId(entry.complaintId)
            complaint.setComplaintDecisionTargetStatus(targetStatus)
            complaint.setComplaintDecisionTextState("")
            complaint.setComplaintDecisionError(null)
        },
        setComplaintDecisionText = { value ->
            complaint.setComplaintDecisionTextState(value)
            complaint.setComplaintDecisionError(null)
        },
        closeComplaintDecisionComposer = {
            if (complaint.isComplaintDecisionSubmitting()) return@IdentityConnectedViewModerationActions
            complaint.setComplaintDecisionEntryId(null)
            complaint.setComplaintDecisionTargetStatus(null)
            complaint.setComplaintDecisionTextState("")
            complaint.setComplaintDecisionError(null)
        },
        submitComplaintDecision = {
            val complaintId = complaint.complaintDecisionEntryId() ?: return@IdentityConnectedViewModerationActions
            val targetStatus = complaint.complaintDecisionTargetStatus() ?: return@IdentityConnectedViewModerationActions
            val entry =
                complaint.complaintQueueEntries().firstOrNull { it.complaintId == complaintId }
                    ?: return@IdentityConnectedViewModerationActions
            val feedback = complaint.complaintDecisionText().trim()
            if (feedback.isEmpty()) {
                complaint.setComplaintDecisionError(
                    dependencies.context.async.translator.translate("connected.complaints.admin.feedbackRequired"),
                )
                return@IdentityConnectedViewModerationActions
            }
            if (1000 < feedback.length) {
                complaint.setComplaintDecisionError(
                    dependencies.context.async.translator.translate("connected.complaints.admin.feedbackTooLong"),
                )
                return@IdentityConnectedViewModerationActions
            }
            complaint.setComplaintDecisionSubmitting(true)
            complaint.setComplaintDecisionError(null)
            dependencies.context.async.scope.launch {
                val success =
                    dependencies.adminPostComplaintService.decide(
                        complaintId = entry.complaintId,
                        postId = entry.complaint.postId,
                        targetStatus = targetStatus,
                        feedback = feedback,
                    )
                complaint.setComplaintDecisionSubmitting(false)
                if (success) {
                    complaint.updateComplaintQueueEntries(
                        complaint.complaintQueueEntries().map { currentEntry ->
                            if (currentEntry.complaintId == complaintId) currentEntry.copy(state = targetStatus) else currentEntry
                        },
                    )
                    complaint.setComplaintDecisionEntryId(null)
                    complaint.setComplaintDecisionTargetStatus(null)
                    complaint.setComplaintDecisionTextState("")
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.complaints.admin.decided"),
                    )
                    refreshComplaintQueue(openAfterLoad = complaint.complaintQueueEntries().size > 1)
                    inbox.refreshInbox(false)
                } else {
                    complaint.setComplaintDecisionError(
                        dependencies.context.async.translator.translate("connected.complaints.admin.error"),
                    )
                }
            }
        },
    )
}
