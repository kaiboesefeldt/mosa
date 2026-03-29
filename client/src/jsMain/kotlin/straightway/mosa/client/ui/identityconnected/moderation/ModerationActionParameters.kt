package straightway.mosa.client.ui.identityconnected.moderation

import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*

import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessageListResult

internal data class ModerationInboxActionDependencies(
    val context: ConnectedViewFeedbackContext,
    val adminMessageInboxService: AdminMessageInboxService,
)

internal data class ModerationComplaintQueueActionDependencies(
    val context: ConnectedViewFeedbackContext,
    val postComplaintService: PostComplaintService,
    val adminPostComplaintService: AdminPostComplaintService,
    val connectedIdentityGid: String?,
)

internal data class ModerationInboxModel(
    val refreshInbox: (Boolean) -> Unit,
    val isOpen: Boolean,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val hasMore: Boolean,
    val page: Int,
    val refreshFromTime: Long,
    val hiddenSeenMessageIds: List<String>,
    val setInboxOpen: (Boolean) -> Unit,
    val setInboxError: (String?) -> Unit,
    val setInboxAppending: (Boolean) -> Unit,
    val applyInboxPage: (AdminMessageListResult, Boolean, List<String>) -> Unit,
    val rememberInboxScrollTop: () -> Unit,
)

internal data class ModerationComplaintQueueModel(
    val isOpen: Boolean,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val hasMore: Boolean,
    val page: Int,
    val showProcessedEntries: Boolean,
    val entries: List<ComplaintQueueEntry>,
    val actionPostId: String?,
    val composerPostId: String?,
    val composerText: String,
    val isSubmitting: Boolean,
    val decisionEntryId: String?,
    val decisionTargetStatus: PostComplaintState?,
    val decisionText: String,
    val isDecisionSubmitting: Boolean,
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
