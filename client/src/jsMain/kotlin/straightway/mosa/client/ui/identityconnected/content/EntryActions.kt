package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

internal data class EntryActions(
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
    val adminContact: AdminContactActions,
)
