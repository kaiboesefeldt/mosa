package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.RefObject
import web.html.HTMLDivElement

internal data class IdentityConnectedViewAdminController(
    val adminUserCardAnchorId: String?,
    val adminMessageRecipientName: String?,
    val adminMessageRecipientAvatar: String?,
    val adminMessageSubject: String,
    val adminMessageBody: String,
    val isAdminMessageSending: Boolean,
    val adminMessageError: String?,
    val isAdminMessageComposerOpen: Boolean,
    val adminHistoryEntries: List<straightway.mosa.message.AdminMessage>,
    val isAdminHistoryLoading: Boolean,
    val isAdminHistoryAppending: Boolean,
    val adminHistoryError: String?,
    val adminHistoryHasMore: Boolean,
    val adminHistoryListRef: RefObject<HTMLDivElement>,
    val toggleAdminUserCard: (String, String, String, String) -> Unit,
    val openAdminMessageCompose: () -> Unit,
    val setAdminMessageSubject: (String) -> Unit,
    val setAdminMessageBody: (String) -> Unit,
    val closeAdminUserCard: () -> Unit,
    val sendAdminMessageToCurrentRecipient: () -> Unit,
    val loadMoreAdminHistory: () -> Unit,
)
