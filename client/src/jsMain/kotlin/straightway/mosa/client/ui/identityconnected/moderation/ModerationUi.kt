package straightway.mosa.client.ui.identityconnected.moderation

import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*

import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.client.i18n.Translator

internal data class ComplaintQueueEntry(
    val complaint: OpenPostComplaint,
    val state: PostComplaintState,
)

internal fun OpenPostComplaint.toQueueEntry(): ComplaintQueueEntry = ComplaintQueueEntry(complaint = this, state = state)

internal val ComplaintQueueEntry.complaintId: String
    get() = complaint.complaintId

internal fun List<ComplaintQueueEntry>.filteredForComplaintQueue(showProcessedEntries: Boolean): List<ComplaintQueueEntry> {
    if (showProcessedEntries) {
        return this
    }
    return filter(ComplaintQueueEntry::isOpen)
}

internal fun ComplaintQueueEntry.isOpen(): Boolean = state == PostComplaintState.OPEN

internal fun ComplaintQueueEntry.canTransitionTo(targetState: PostComplaintState): Boolean =
    when (state) {
        PostComplaintState.OPEN -> targetState != PostComplaintState.OPEN
        PostComplaintState.REJECTED -> targetState == PostComplaintState.CONFIRMED
        PostComplaintState.CONFIRMED -> targetState == PostComplaintState.REJECTED
        PostComplaintState.DELETED -> false
    }

internal fun ComplaintQueueEntry.availableActions(): List<PostComplaintState> =
    when (state) {
        PostComplaintState.OPEN ->
            listOf(
                PostComplaintState.CONFIRMED,
                PostComplaintState.REJECTED,
                PostComplaintState.DELETED,
            )
        PostComplaintState.REJECTED -> listOf(PostComplaintState.CONFIRMED)
        PostComplaintState.CONFIRMED -> listOf(PostComplaintState.REJECTED)
        PostComplaintState.DELETED -> emptyList()
    }

internal fun complaintDecisionTitle(
    translator: Translator,
    targetStatus: PostComplaintState?,
): String =
    when (targetStatus) {
        PostComplaintState.CONFIRMED -> translator.translate("connected.complaints.admin.composeConfirmed")
        PostComplaintState.REJECTED -> translator.translate("connected.complaints.admin.composeRejected")
        PostComplaintState.DELETED -> translator.translate("connected.complaints.admin.composeDeleted")
        PostComplaintState.OPEN, null -> translator.translate("connected.complaints.admin.title")
    }

internal fun complaintQueueActionLabelKey(targetState: PostComplaintState): String =
    when (targetState) {
        PostComplaintState.CONFIRMED -> "connected.complaints.admin.confirm"
        PostComplaintState.REJECTED -> "connected.complaints.admin.reject"
        PostComplaintState.DELETED -> "connected.complaints.admin.delete"
        PostComplaintState.OPEN -> "connected.complaints.admin.title"
    }

internal fun complaintQueueStateLabel(
    translator: Translator,
    state: PostComplaintState,
): String? =
    when (state) {
        PostComplaintState.OPEN -> null
        PostComplaintState.CONFIRMED -> translator.translate("connected.complaints.admin.stateConfirmed")
        PostComplaintState.REJECTED -> translator.translate("connected.complaints.admin.stateRejected")
        PostComplaintState.DELETED -> translator.translate("connected.complaints.admin.stateDeleted")
    }
