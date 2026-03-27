package straightway.mosa.client.ui.identityconnected

internal data class IdentityConnectedViewEntryActions(
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
    val adminContact: IdentityConnectedViewAdminContactActions,
)
