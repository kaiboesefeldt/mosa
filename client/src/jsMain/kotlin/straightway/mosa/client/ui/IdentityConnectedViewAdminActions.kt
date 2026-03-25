package straightway.mosa.client.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.client.i18n.Translator
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult

internal data class IdentityConnectedViewAdminActions(
    val toggleAdminUserCard: (String, String, String, String) -> Unit,
    val openAdminMessageCompose: () -> Unit,
    val setAdminMessageSubject: (String) -> Unit,
    val setAdminMessageBody: (String) -> Unit,
    val closeAdminUserCard: () -> Unit,
    val sendAdminMessageToCurrentRecipient: () -> Unit,
    val loadMoreAdminHistory: () -> Unit,
)

internal fun buildIdentityConnectedViewAdminActions(
    translator: Translator,
    adminMessageHistoryService: AdminMessageHistoryService,
    adminMessagePublisher: AdminMessagePublisher,
    scope: CoroutineScope,
    canDeleteAsAdmin: () -> Boolean,
    showToast: (String) -> Unit,
    adminUserCardAnchorId: () -> String?,
    adminMessageRecipientGid: () -> String?,
    adminMessageSubject: () -> String,
    adminMessageBody: () -> String,
    isAdminMessageSending: () -> Boolean,
    isAdminHistoryLoading: () -> Boolean,
    isAdminHistoryAppending: () -> Boolean,
    adminHistoryHasMore: () -> Boolean,
    adminHistoryPage: () -> Int,
    adminHistoryEntries: () -> List<AdminMessage>,
    setAdminUserCardAnchorId: (String?) -> Unit,
    setAdminMessageRecipientGid: (String?) -> Unit,
    setAdminMessageRecipientName: (String?) -> Unit,
    setAdminMessageRecipientAvatar: (String?) -> Unit,
    setAdminMessageSubjectState: (String) -> Unit,
    setAdminMessageBodyState: (String) -> Unit,
    setAdminMessageSending: (Boolean) -> Unit,
    setAdminMessageError: (String?) -> Unit,
    setAdminMessageComposerOpen: (Boolean) -> Unit,
    setAdminHistoryEntries: (List<AdminMessage>) -> Unit,
    setAdminHistoryLoading: (Boolean) -> Unit,
    setAdminHistoryAppending: (Boolean) -> Unit,
    setAdminHistoryError: (String?) -> Unit,
    setAdminHistoryPage: (Int) -> Unit,
    setAdminHistoryHasMore: (Boolean) -> Unit,
    rememberAdminHistoryScrollTop: () -> Unit,
): IdentityConnectedViewAdminActions {
    fun applyAdminHistoryPage(result: AdminMessageListResult, append: Boolean) {
        val nextEntries =
            if (append) {
                (adminHistoryEntries() + result.entries).distinctBy(AdminMessage::id)
            } else {
                result.entries
            }
        setAdminHistoryEntries(nextEntries)
        setAdminHistoryPage(result.page)
        setAdminHistoryHasMore(result.hasMore)
    }

    fun closeAdminUserCard() {
        if (isAdminMessageSending()) return
        setAdminUserCardAnchorId(null)
        setAdminMessageRecipientGid(null)
        setAdminMessageRecipientName(null)
        setAdminMessageRecipientAvatar(null)
        setAdminMessageSubjectState("")
        setAdminMessageBodyState("")
        setAdminMessageError(null)
        setAdminMessageComposerOpen(false)
        setAdminHistoryEntries(emptyList())
        setAdminHistoryLoading(false)
        setAdminHistoryAppending(false)
        setAdminHistoryError(null)
        setAdminHistoryPage(1)
        setAdminHistoryHasMore(false)
    }

    fun refreshAdminHistory(recipientGid: String) {
        setAdminHistoryLoading(true)
        setAdminHistoryAppending(false)
        setAdminHistoryError(null)
        scope.launch {
            val result = runCatching { adminMessageHistoryService.fetch(recipientGid, page = 1) }.getOrNull()
            setAdminHistoryLoading(false)
            if (result == null) {
                setAdminHistoryEntries(emptyList())
                setAdminHistoryPage(1)
                setAdminHistoryHasMore(false)
                setAdminHistoryError(translator.translate("connected.messages.actions.history.error"))
            } else {
                applyAdminHistoryPage(result, append = false)
            }
        }
    }

    return IdentityConnectedViewAdminActions(
        toggleAdminUserCard = { anchorId, recipientGid, recipientName, recipientAvatar ->
            if (!canDeleteAsAdmin()) return@IdentityConnectedViewAdminActions
            if (adminUserCardAnchorId() == anchorId) {
                closeAdminUserCard()
                return@IdentityConnectedViewAdminActions
            }
            setAdminUserCardAnchorId(anchorId)
            setAdminMessageRecipientGid(recipientGid)
            setAdminMessageRecipientName(recipientName)
            setAdminMessageRecipientAvatar(recipientAvatar)
            setAdminMessageSubjectState("")
            setAdminMessageBodyState("")
            setAdminMessageError(null)
            setAdminMessageComposerOpen(false)
            refreshAdminHistory(recipientGid)
        },
        openAdminMessageCompose = {
            if (adminUserCardAnchorId() == null || adminMessageRecipientGid() == null) return@IdentityConnectedViewAdminActions
            setAdminMessageError(null)
            setAdminMessageComposerOpen(true)
        },
        setAdminMessageSubject = { value -> setAdminMessageSubjectState(value) },
        setAdminMessageBody = { value -> setAdminMessageBodyState(value) },
        closeAdminUserCard = ::closeAdminUserCard,
        sendAdminMessageToCurrentRecipient = {
            val recipientGid = adminMessageRecipientGid() ?: return@IdentityConnectedViewAdminActions
            scope.launch {
                setAdminMessageSending(true)
                setAdminMessageError(null)
                val wasSent =
                    runCatching {
                        adminMessagePublisher.send(
                            recipientGid,
                            adminMessageSubject(),
                            adminMessageBody(),
                        )
                    }.getOrDefault(false)
                setAdminMessageSending(false)
                if (wasSent) {
                    setAdminMessageSubjectState("")
                    setAdminMessageBodyState("")
                    setAdminMessageError(null)
                    setAdminMessageComposerOpen(false)
                    refreshAdminHistory(recipientGid)
                    showToast(translator.translate("connected.messages.compose.success"))
                } else {
                    setAdminMessageError(translator.translate("connected.messages.compose.error"))
                }
            }
        },
        loadMoreAdminHistory = {
            val recipientGid = adminMessageRecipientGid() ?: return@IdentityConnectedViewAdminActions
            if (isAdminHistoryLoading() || isAdminHistoryAppending() || !adminHistoryHasMore()) {
                return@IdentityConnectedViewAdminActions
            }
            rememberAdminHistoryScrollTop()
            setAdminHistoryAppending(true)
            setAdminHistoryError(null)
            val nextPage = adminHistoryPage() + 1
            scope.launch {
                val result = runCatching { adminMessageHistoryService.fetch(recipientGid, page = nextPage) }.getOrNull()
                setAdminHistoryAppending(false)
                if (result == null) {
                    setAdminHistoryError(translator.translate("connected.messages.actions.history.error"))
                } else {
                    applyAdminHistoryPage(result, append = true)
                }
            }
        },
    )
}
