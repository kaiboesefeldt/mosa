package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

import straightway.mosa.message.AdminMessage
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher

internal data class AdminActionDependencies(
    val context: ConnectedViewFeedbackContext,
    val adminMessageHistoryService: AdminMessageHistoryService,
    val adminMessagePublisher: AdminMessagePublisher,
    val canDeleteAsAdmin: Boolean,
)

internal data class AdminRecipientModel(
    val anchorId: String?,
    val recipientGid: String?,
    val setAnchorId: (String?) -> Unit,
    val setRecipientGid: (String?) -> Unit,
    val setRecipientName: (String?) -> Unit,
    val setRecipientAvatar: (String?) -> Unit,
)

internal data class AdminComposerModel(
    val subject: String,
    val body: String,
    val isSending: Boolean,
    val setSubject: (String) -> Unit,
    val setBody: (String) -> Unit,
    val setSending: (Boolean) -> Unit,
    val setError: (String?) -> Unit,
    val setOpen: (Boolean) -> Unit,
)

internal data class AdminHistoryModel(
    val entries: List<AdminMessage>,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val hasMore: Boolean,
    val page: Int,
    val setEntries: (List<AdminMessage>) -> Unit,
    val setLoading: (Boolean) -> Unit,
    val setAppending: (Boolean) -> Unit,
    val setError: (String?) -> Unit,
    val setPage: (Int) -> Unit,
    val setHasMore: (Boolean) -> Unit,
    val rememberAdminHistoryScrollTop: () -> Unit,
)
