package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.filteredForInbox
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
) {
    val visibleInboxEntries: List<AdminMessage>
        get() = inboxEntries.filteredForInbox(showSeenInboxEntries, hiddenSeenInboxMessageIds)

    val hasHiddenSeenInboxEntries: Boolean
        get() = hiddenSeenInboxMessageIds.isNotEmpty()

    val hasReadInboxEntries: Boolean
        get() = seenInboxMessageIds.isNotEmpty()

    val visibleComplaintQueueEntries: List<ComplaintQueueEntry>
        get() = complaintQueueEntries.filteredForComplaintQueue(showProcessedComplaintEntries)

    val hasHiddenProcessedComplaintEntries: Boolean
        get() = complaintQueueEntries.any { !it.isOpen() }
}

internal data class IdentityConnectedViewModerationControllerRefs(
    val inboxButtonRef: RefObject<HTMLButtonElement>,
    val complaintQueueButtonRef: RefObject<HTMLButtonElement>,
    val inboxPopoverRef: RefObject<HTMLDivElement>,
    val complaintQueuePopoverRef: RefObject<HTMLDivElement>,
)

internal data class IdentityConnectedViewModerationControllerActions(
    val toggleSeenInboxVisibility: () -> Unit,
    val loadInbox: () -> Unit,
    val closeInbox: () -> Unit,
    val loadMoreInbox: () -> Unit,
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
