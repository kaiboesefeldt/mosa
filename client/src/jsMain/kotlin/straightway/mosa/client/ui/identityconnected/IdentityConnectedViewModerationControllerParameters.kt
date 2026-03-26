package straightway.mosa.client.ui.identityconnected

import react.RefObject
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.ui.AdminMessageInboxStateStore
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessage
import web.html.HTMLButtonElement
import web.html.HTMLDivElement

internal data class IdentityConnectedViewModerationControllerDependencies(
    val context: IdentityConnectedViewFeedbackContext,
    val inboxStateStore: AdminMessageInboxStateStore,
    val adminMessageInboxService: AdminMessageInboxService,
    val postComplaintService: PostComplaintService,
    val adminPostComplaintService: AdminPostComplaintService,
    val podBaseProvider: PodBaseProvider,
    val connectedIdentityGid: String?,
    val showsConnectedAdminBadge: Boolean,
)

internal data class IdentityConnectedViewModerationControllerState(
    val isInboxOpen: Boolean,
    val isInboxLoading: Boolean,
    val isInboxAppending: Boolean,
    val inboxEntries: List<AdminMessage>,
    val showSeenInboxEntries: Boolean,
    val hiddenSeenInboxMessageIds: List<String>,
    val inboxError: String?,
    val inboxHasMore: Boolean,
    val seenInboxMessageIds: List<String>,
    val unreadInboxCount: Int,
    val isComplaintQueueOpen: Boolean,
    val isComplaintQueueLoading: Boolean,
    val isComplaintQueueAppending: Boolean,
    val complaintQueueEntries: List<ComplaintQueueEntry>,
    val complaintQueueError: String?,
    val complaintQueueHasMore: Boolean,
    val showProcessedComplaintEntries: Boolean,
    val openComplaintCount: Int,
    val complaintActionPostId: String?,
    val complaintComposerPostId: String?,
    val complaintComposerText: String,
    val complaintComposerError: String?,
    val isComplaintSubmitting: Boolean,
    val complaintDecisionEntryId: String?,
    val complaintDecisionTargetStatus: PostComplaintState?,
    val complaintDecisionText: String,
    val complaintDecisionError: String?,
    val isComplaintDecisionSubmitting: Boolean,
)

internal data class IdentityConnectedViewModerationControllerRefs(
    val inboxButtonRef: RefObject<HTMLButtonElement>,
    val complaintQueueButtonRef: RefObject<HTMLButtonElement>,
    val inboxPopoverRef: RefObject<HTMLDivElement>,
    val complaintQueuePopoverRef: RefObject<HTMLDivElement>,
)

internal data class IdentityConnectedViewModerationControllerCallbacks(
    val toggleSeenInboxVisibility: () -> Unit,
    val actions: IdentityConnectedViewModerationActions,
)
