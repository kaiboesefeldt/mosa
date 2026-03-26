package straightway.mosa.client.ui.identityconnected

import straightway.mosa.message.AdminMessage
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher

internal data class IdentityConnectedViewAdminActionDependencies(
    val context: IdentityConnectedViewFeedbackContext,
    val adminMessageHistoryService: AdminMessageHistoryService,
    val adminMessagePublisher: AdminMessagePublisher,
    val canDeleteAsAdmin: () -> Boolean,
)

internal data class IdentityConnectedViewAdminActionState(
    val adminUserCardAnchorId: () -> String?,
    val adminMessageRecipientGid: () -> String?,
    val adminMessageSubject: () -> String,
    val adminMessageBody: () -> String,
    val isAdminMessageSending: () -> Boolean,
    val isAdminHistoryLoading: () -> Boolean,
    val isAdminHistoryAppending: () -> Boolean,
    val adminHistoryHasMore: () -> Boolean,
    val adminHistoryPage: () -> Int,
    val adminHistoryEntries: () -> List<AdminMessage>,
)

internal data class IdentityConnectedViewAdminActionMutations(
    val setAdminUserCardAnchorId: (String?) -> Unit,
    val setAdminMessageRecipientGid: (String?) -> Unit,
    val setAdminMessageRecipientName: (String?) -> Unit,
    val setAdminMessageRecipientAvatar: (String?) -> Unit,
    val setAdminMessageSubjectState: (String) -> Unit,
    val setAdminMessageBodyState: (String) -> Unit,
    val setAdminMessageSending: (Boolean) -> Unit,
    val setAdminMessageError: (String?) -> Unit,
    val setAdminMessageComposerOpen: (Boolean) -> Unit,
    val setAdminHistoryEntries: (List<AdminMessage>) -> Unit,
    val setAdminHistoryLoading: (Boolean) -> Unit,
    val setAdminHistoryAppending: (Boolean) -> Unit,
    val setAdminHistoryError: (String?) -> Unit,
    val setAdminHistoryPage: (Int) -> Unit,
    val setAdminHistoryHasMore: (Boolean) -> Unit,
    val rememberAdminHistoryScrollTop: () -> Unit,
)
