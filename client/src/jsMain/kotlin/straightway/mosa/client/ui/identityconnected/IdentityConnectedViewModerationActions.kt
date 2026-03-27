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
    inbox: IdentityConnectedViewModerationInboxModel,
    complaint: IdentityConnectedViewModerationComplaintQueueModel,
): IdentityConnectedViewModerationActions {
    fun refreshComplaintQueue(
        openAfterLoad: Boolean,
        includeProcessed: Boolean = complaint.showProcessedEntries,
    ) {
        if (complaint.isLoading || complaint.isAppending) return
        complaint.setComplaintQueueOpen(openAfterLoad || complaint.isOpen)
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
            if (!inbox.isOpen || inbox.isLoading || inbox.isAppending || !inbox.hasMore) {
                return@IdentityConnectedViewModerationActions
            }
            inbox.rememberInboxScrollTop()
            inbox.setInboxAppending(true)
            inbox.setInboxError(null)
            val nextPage = inbox.page + 1
            dependencies.context.async.scope.launch {
                val result =
                    runCatching {
                        dependencies.adminMessageInboxService.fetch(page = nextPage, fromTime = inbox.refreshFromTime)
                    }.getOrNull()
                inbox.setInboxAppending(false)
                if (result == null) {
                    inbox.setInboxError(dependencies.context.async.translator.translate("connected.messages.inbox.error"))
                } else {
                    inbox.applyInboxPage(result, true, inbox.hiddenSeenMessageIds)
                }
            }
        },
        loadComplaintQueue = { refreshComplaintQueue(true) },
        closeComplaintQueue = { complaint.setComplaintQueueOpen(false) },
        toggleProcessedComplaintVisibility = {
            val willShowProcessedEntries = !complaint.showProcessedEntries
            complaint.setShowProcessedComplaintEntries(willShowProcessedEntries)
            if (complaint.isOpen) {
                refreshComplaintQueue(openAfterLoad = true, includeProcessed = willShowProcessedEntries)
            }
        },
        loadMoreComplaintQueue = {
            if (
                !complaint.isOpen ||
                complaint.isLoading ||
                complaint.isAppending ||
                !complaint.hasMore
            ) {
                return@IdentityConnectedViewModerationActions
            }
            complaint.setComplaintQueueAppending(true)
            complaint.setComplaintQueueError(null)
            val nextPage = complaint.page + 1
            val includeProcessed = complaint.showProcessedEntries
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
                complaint.actionPostId != null ||
                complaint.composerPostId != null ||
                dependencies.connectedIdentityGid == null
            ) {
                return@IdentityConnectedViewModerationActions
            }
            if (entry.authorGID == dependencies.connectedIdentityGid) return@IdentityConnectedViewModerationActions
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
            if (complaint.isSubmitting) return@IdentityConnectedViewModerationActions
            complaint.setComplaintComposerPostId(null)
            complaint.setComplaintComposerTextState("")
            complaint.setComplaintComposerError(null)
        },
        submitPostComplaint = {
            val postId = complaint.composerPostId ?: return@IdentityConnectedViewModerationActions
            val reason = complaint.composerText.trim()
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
            if (!entry.canTransitionTo(targetStatus) || complaint.isDecisionSubmitting) {
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
            if (complaint.isDecisionSubmitting) return@IdentityConnectedViewModerationActions
            complaint.setComplaintDecisionEntryId(null)
            complaint.setComplaintDecisionTargetStatus(null)
            complaint.setComplaintDecisionTextState("")
            complaint.setComplaintDecisionError(null)
        },
        submitComplaintDecision = {
            val complaintId = complaint.decisionEntryId ?: return@IdentityConnectedViewModerationActions
            val targetStatus = complaint.decisionTargetStatus ?: return@IdentityConnectedViewModerationActions
            val entry =
                complaint.entries.firstOrNull { it.complaintId == complaintId }
                    ?: return@IdentityConnectedViewModerationActions
            val feedback = complaint.decisionText.trim()
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
                        complaint.entries.map { currentEntry ->
                            if (currentEntry.complaintId == complaintId) currentEntry.copy(state = targetStatus) else currentEntry
                        },
                    )
                    complaint.setComplaintDecisionEntryId(null)
                    complaint.setComplaintDecisionTargetStatus(null)
                    complaint.setComplaintDecisionTextState("")
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.complaints.admin.decided"),
                    )
                    refreshComplaintQueue(openAfterLoad = complaint.entries.size > 1)
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
