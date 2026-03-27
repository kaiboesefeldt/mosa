package straightway.mosa.client.ui.identityconnected.moderation

import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*

import straightway.mosa.client.ui.IdentityVisualsFactory
import react.Ref
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessage
import web.html.HTMLDivElement

internal data class AdminMessageInboxPopoverProps(
    val translator: Translator,
    val identityVisualsFactory: IdentityVisualsFactory,
    val entries: List<AdminMessage>,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val errorText: String?,
    val hasMore: Boolean,
    val showSeenMessages: Boolean,
    val hasHiddenSeenEntries: Boolean,
    val readMessageIds: List<String>,
    val hasReadEntries: Boolean,
    val popoverRef: Ref<HTMLDivElement>,
    val onToggleSeenVisibility: () -> Unit,
    val onClose: () -> Unit,
    val onScrollToEnd: () -> Unit,
)

internal data class PostComplaintQueuePopoverProps(
    val translator: Translator,
    val identityVisualsFactory: IdentityVisualsFactory,
    val entries: List<ComplaintQueueEntry>,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val errorText: String?,
    val hasMore: Boolean,
    val showProcessedEntries: Boolean,
    val hasHiddenProcessedEntries: Boolean,
    val popoverRef: Ref<HTMLDivElement>,
    val onToggleProcessedVisibility: () -> Unit,
    val onClose: () -> Unit,
    val onLoadMore: () -> Unit,
    val decisionEntryId: String?,
    val decisionTargetStatus: PostComplaintState?,
    val decisionText: String,
    val decisionErrorText: String?,
    val isDecisionSubmitting: Boolean,
    val onDecisionTextChange: (String) -> Unit,
    val onCloseDecisionComposer: () -> Unit,
    val onSubmitDecision: () -> Unit,
    val onConfirm: (ComplaintQueueEntry) -> Unit,
    val onReject: (ComplaintQueueEntry) -> Unit,
    val onDelete: (ComplaintQueueEntry) -> Unit,
)
