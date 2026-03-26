package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.IdentityVisualsFactory
import straightway.mosa.client.content.Topic
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.QueryResult

internal data class ReplyCardProps(
    val translator: Translator,
    val visualsFactory: IdentityVisualsFactory,
    val reply: QueryResult.Entry,
    val connectedGid: String?,
    val canDeleteAsAdmin: Boolean,
    val deleteCandidateId: String?,
    val isDeleting: Boolean,
    val deleteError: String?,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val canComplain: Boolean,
    val isComplaining: Boolean,
    val complaintComposerPostId: String?,
    val complaintComposerText: String,
    val complaintComposerError: String?,
    val isComplaintSubmitting: Boolean,
    val onRequestComplaint: () -> Unit,
    val onComplaintTextChange: (String) -> Unit,
    val onCloseComplaintComposer: () -> Unit,
    val onSubmitComplaint: () -> Unit,
    val canComposeAdminMessage: Boolean,
    val onToggleAdminUserCard: (String, String, String, String) -> Unit,
)

internal data class PostCardProps(
    val translator: Translator,
    val visualsFactory: IdentityVisualsFactory,
    val entry: QueryResult.Entry,
    val topic: Topic,
    val isFilterEntry: Boolean,
    val onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    val isSelected: Boolean,
    val onSelect: () -> Unit,
    val connectedGid: String?,
    val canDeleteAsAdmin: Boolean,
    val deleteCandidateId: String?,
    val isDeleting: Boolean,
    val deleteError: String?,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val canComplain: Boolean,
    val isComplaining: Boolean,
    val complaintComposerPostId: String?,
    val complaintComposerText: String,
    val complaintComposerError: String?,
    val isComplaintSubmitting: Boolean,
    val onRequestComplaint: () -> Unit,
    val onComplaintTextChange: (String) -> Unit,
    val onCloseComplaintComposer: () -> Unit,
    val onSubmitComplaint: () -> Unit,
    val canComposeAdminMessage: Boolean,
    val onToggleAdminUserCard: (String, String, String, String) -> Unit,
)
