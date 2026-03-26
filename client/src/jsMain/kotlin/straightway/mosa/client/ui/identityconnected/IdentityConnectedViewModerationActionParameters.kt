package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessageListResult

internal data class IdentityConnectedViewModerationActionDependencies(
    val context: IdentityConnectedViewFeedbackContext,
    val adminMessageInboxService: AdminMessageInboxService,
    val postComplaintService: PostComplaintService,
    val adminPostComplaintService: AdminPostComplaintService,
    val connectedIdentityGid: () -> String?,
)

internal data class IdentityConnectedViewModerationInboxState(
    val refreshInbox: (Boolean) -> Unit,
    val isInboxOpen: () -> Boolean,
    val isInboxLoading: () -> Boolean,
    val isInboxAppending: () -> Boolean,
    val inboxHasMore: () -> Boolean,
    val inboxPage: () -> Int,
    val inboxRefreshFromTime: () -> Long,
    val hiddenSeenInboxMessageIds: () -> List<String>,
    val setInboxOpen: (Boolean) -> Unit,
    val setInboxError: (String?) -> Unit,
    val setInboxAppending: (Boolean) -> Unit,
    val applyInboxPage: (AdminMessageListResult, Boolean, List<String>) -> Unit,
    val rememberInboxScrollTop: () -> Unit,
)

internal data class IdentityConnectedViewModerationComplaintState(
    val isComplaintQueueOpen: () -> Boolean,
    val isComplaintQueueLoading: () -> Boolean,
    val isComplaintQueueAppending: () -> Boolean,
    val complaintQueueHasMore: () -> Boolean,
    val complaintQueuePage: () -> Int,
    val showProcessedComplaintEntries: () -> Boolean,
    val complaintQueueEntries: () -> List<ComplaintQueueEntry>,
    val complaintActionPostId: () -> String?,
    val complaintComposerPostId: () -> String?,
    val complaintComposerText: () -> String,
    val isComplaintSubmitting: () -> Boolean,
    val complaintDecisionEntryId: () -> String?,
    val complaintDecisionTargetStatus: () -> PostComplaintState?,
    val complaintDecisionText: () -> String,
    val isComplaintDecisionSubmitting: () -> Boolean,
    val setComplaintQueueOpen: (Boolean) -> Unit,
    val setComplaintQueueLoading: (Boolean) -> Unit,
    val setComplaintQueueAppending: (Boolean) -> Unit,
    val setComplaintQueueError: (String?) -> Unit,
    val setComplaintQueuePage: (Int) -> Unit,
    val setComplaintQueueHasMore: (Boolean) -> Unit,
    val applyComplaintQueuePage: (OpenPostComplaintsResult, Boolean) -> Unit,
    val refreshComplaintCount: (Boolean) -> Unit,
    val setShowProcessedComplaintEntries: (Boolean) -> Unit,
    val setComplaintActionPostId: (String?) -> Unit,
    val setComplaintComposerPostId: (String?) -> Unit,
    val setComplaintComposerTextState: (String) -> Unit,
    val setComplaintComposerError: (String?) -> Unit,
    val setComplaintSubmitting: (Boolean) -> Unit,
    val setComplaintDecisionEntryId: (String?) -> Unit,
    val setComplaintDecisionTargetStatus: (PostComplaintState?) -> Unit,
    val setComplaintDecisionTextState: (String) -> Unit,
    val setComplaintDecisionError: (String?) -> Unit,
    val setComplaintDecisionSubmitting: (Boolean) -> Unit,
    val updateComplaintQueueEntries: (List<ComplaintQueueEntry>) -> Unit,
)
