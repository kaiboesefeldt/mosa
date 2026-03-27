package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

import react.RefObject
import straightway.mosa.message.AdminMessage
import web.html.HTMLDivElement

internal data class AdminControllerState(
    val adminUserCardAnchorId: String?,
    val adminMessageRecipientName: String?,
    val adminMessageRecipientAvatar: String?,
    val adminMessageSubject: String,
    val adminMessageBody: String,
    val isAdminMessageSending: Boolean,
    val adminMessageError: String?,
    val isAdminMessageComposerOpen: Boolean,
    val adminHistoryEntries: List<AdminMessage>,
    val isAdminHistoryLoading: Boolean,
    val isAdminHistoryAppending: Boolean,
    val adminHistoryError: String?,
    val adminHistoryHasMore: Boolean,
)

internal data class AdminControllerRefs(
    val adminHistoryListRef: RefObject<HTMLDivElement>,
)

internal data class AdminControllerActions(
    val toggleAdminUserCard: (String, String, String, String) -> Unit,
    val openAdminMessageCompose: () -> Unit,
    val setAdminMessageSubject: (String) -> Unit,
    val setAdminMessageBody: (String) -> Unit,
    val closeAdminUserCard: () -> Unit,
    val sendAdminMessageToCurrentRecipient: () -> Unit,
    val loadMoreAdminHistory: () -> Unit,
)

internal data class AdminController(
    val state: AdminControllerState,
    val refs: AdminControllerRefs,
    val actions: AdminControllerActions,
)
