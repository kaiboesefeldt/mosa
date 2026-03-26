package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.coroutines.launch
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher
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
    dependencies: IdentityConnectedViewAdminActionDependencies,
    state: IdentityConnectedViewAdminActionState,
    mutations: IdentityConnectedViewAdminActionMutations,
): IdentityConnectedViewAdminActions {
    fun applyAdminHistoryPage(result: AdminMessageListResult, append: Boolean) {
        val nextEntries =
            if (append) {
                (state.adminHistoryEntries() + result.entries).distinctBy(AdminMessage::id)
            } else {
                result.entries
            }
        mutations.setAdminHistoryEntries(nextEntries)
        mutations.setAdminHistoryPage(result.page)
        mutations.setAdminHistoryHasMore(result.hasMore)
    }

    fun closeAdminUserCard() {
        if (state.isAdminMessageSending()) return
        mutations.setAdminUserCardAnchorId(null)
        mutations.setAdminMessageRecipientGid(null)
        mutations.setAdminMessageRecipientName(null)
        mutations.setAdminMessageRecipientAvatar(null)
        mutations.setAdminMessageSubjectState("")
        mutations.setAdminMessageBodyState("")
        mutations.setAdminMessageError(null)
        mutations.setAdminMessageComposerOpen(false)
        mutations.setAdminHistoryEntries(emptyList())
        mutations.setAdminHistoryLoading(false)
        mutations.setAdminHistoryAppending(false)
        mutations.setAdminHistoryError(null)
        mutations.setAdminHistoryPage(1)
        mutations.setAdminHistoryHasMore(false)
    }

    fun refreshAdminHistory(recipientGid: String) {
        mutations.setAdminHistoryLoading(true)
        mutations.setAdminHistoryAppending(false)
        mutations.setAdminHistoryError(null)
        dependencies.context.async.scope.launch {
            val result = runCatching { dependencies.adminMessageHistoryService.fetch(recipientGid, page = 1) }.getOrNull()
            mutations.setAdminHistoryLoading(false)
            if (result == null) {
                mutations.setAdminHistoryEntries(emptyList())
                mutations.setAdminHistoryPage(1)
                mutations.setAdminHistoryHasMore(false)
                mutations.setAdminHistoryError(dependencies.context.async.translator.translate("connected.messages.actions.history.error"))
            } else {
                applyAdminHistoryPage(result, append = false)
            }
        }
    }

    return IdentityConnectedViewAdminActions(
        toggleAdminUserCard = { anchorId, recipientGid, recipientName, recipientAvatar ->
            if (!dependencies.canDeleteAsAdmin()) return@IdentityConnectedViewAdminActions
            if (state.adminUserCardAnchorId() == anchorId) {
                closeAdminUserCard()
                return@IdentityConnectedViewAdminActions
            }
            mutations.setAdminUserCardAnchorId(anchorId)
            mutations.setAdminMessageRecipientGid(recipientGid)
            mutations.setAdminMessageRecipientName(recipientName)
            mutations.setAdminMessageRecipientAvatar(recipientAvatar)
            mutations.setAdminMessageSubjectState("")
            mutations.setAdminMessageBodyState("")
            mutations.setAdminMessageError(null)
            mutations.setAdminMessageComposerOpen(false)
            refreshAdminHistory(recipientGid)
        },
        openAdminMessageCompose = {
            if (state.adminUserCardAnchorId() == null || state.adminMessageRecipientGid() == null) return@IdentityConnectedViewAdminActions
            mutations.setAdminMessageError(null)
            mutations.setAdminMessageComposerOpen(true)
        },
        setAdminMessageSubject = { value -> mutations.setAdminMessageSubjectState(value) },
        setAdminMessageBody = { value -> mutations.setAdminMessageBodyState(value) },
        closeAdminUserCard = ::closeAdminUserCard,
        sendAdminMessageToCurrentRecipient = {
            val recipientGid = state.adminMessageRecipientGid() ?: return@IdentityConnectedViewAdminActions
            dependencies.context.async.scope.launch {
                mutations.setAdminMessageSending(true)
                mutations.setAdminMessageError(null)
                val wasSent =
                    runCatching {
                        dependencies.adminMessagePublisher.send(
                            recipientGid,
                            state.adminMessageSubject(),
                            state.adminMessageBody(),
                        )
                    }.getOrDefault(false)
                mutations.setAdminMessageSending(false)
                if (wasSent) {
                    mutations.setAdminMessageSubjectState("")
                    mutations.setAdminMessageBodyState("")
                    mutations.setAdminMessageError(null)
                    mutations.setAdminMessageComposerOpen(false)
                    refreshAdminHistory(recipientGid)
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.messages.compose.success"),
                    )
                } else {
                    mutations.setAdminMessageError(dependencies.context.async.translator.translate("connected.messages.compose.error"))
                }
            }
        },
        loadMoreAdminHistory = {
            val recipientGid = state.adminMessageRecipientGid() ?: return@IdentityConnectedViewAdminActions
            if (state.isAdminHistoryLoading() || state.isAdminHistoryAppending() || !state.adminHistoryHasMore()) {
                return@IdentityConnectedViewAdminActions
            }
            mutations.rememberAdminHistoryScrollTop()
            mutations.setAdminHistoryAppending(true)
            mutations.setAdminHistoryError(null)
            val nextPage = state.adminHistoryPage() + 1
            dependencies.context.async.scope.launch {
                val result = runCatching { dependencies.adminMessageHistoryService.fetch(recipientGid, page = nextPage) }.getOrNull()
                mutations.setAdminHistoryAppending(false)
                if (result == null) {
                    mutations.setAdminHistoryError(dependencies.context.async.translator.translate("connected.messages.actions.history.error"))
                } else {
                    applyAdminHistoryPage(result, append = true)
                }
            }
        },
    )
}
