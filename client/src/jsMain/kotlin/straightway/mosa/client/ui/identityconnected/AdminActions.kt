package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.coroutines.launch
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult

internal data class AdminActions(
    val toggleAdminUserCard: (String, String, String, String) -> Unit,
    val openAdminMessageCompose: () -> Unit,
    val setAdminMessageSubject: (String) -> Unit,
    val setAdminMessageBody: (String) -> Unit,
    val closeAdminUserCard: () -> Unit,
    val sendAdminMessageToCurrentRecipient: () -> Unit,
    val loadMoreAdminHistory: () -> Unit,
)

internal fun buildAdminActions(
    dependencies: AdminActionDependencies,
    recipient: AdminRecipientModel,
    composer: AdminComposerModel,
    history: AdminHistoryModel,
): AdminActions {
    fun applyAdminHistoryPage(result: AdminMessageListResult, append: Boolean) {
        val nextEntries =
            if (append) {
                (history.entries + result.entries).distinctBy(AdminMessage::id)
            } else {
                result.entries
            }
        history.setEntries(nextEntries)
        history.setPage(result.page)
        history.setHasMore(result.hasMore)
    }

    fun closeAdminUserCard() {
        if (composer.isSending) return
        recipient.setAnchorId(null)
        recipient.setRecipientGid(null)
        recipient.setRecipientName(null)
        recipient.setRecipientAvatar(null)
        composer.setSubject("")
        composer.setBody("")
        composer.setError(null)
        composer.setOpen(false)
        history.setEntries(emptyList())
        history.setLoading(false)
        history.setAppending(false)
        history.setError(null)
        history.setPage(1)
        history.setHasMore(false)
    }

    fun refreshAdminHistory(recipientGid: String) {
        history.setLoading(true)
        history.setAppending(false)
        history.setError(null)
        dependencies.context.async.scope.launch {
            val result = runCatching { dependencies.adminMessageHistoryService.fetch(recipientGid, page = 1) }.getOrNull()
            history.setLoading(false)
            if (result == null) {
                history.setEntries(emptyList())
                history.setPage(1)
                history.setHasMore(false)
                history.setError(dependencies.context.async.translator.translate("connected.messages.actions.history.error"))
            } else {
                applyAdminHistoryPage(result, append = false)
            }
        }
    }

    return AdminActions(
        toggleAdminUserCard = { anchorId, recipientGid, recipientName, recipientAvatar ->
            if (!dependencies.canDeleteAsAdmin) return@AdminActions
            if (recipient.anchorId == anchorId) {
                closeAdminUserCard()
                return@AdminActions
            }
            recipient.setAnchorId(anchorId)
            recipient.setRecipientGid(recipientGid)
            recipient.setRecipientName(recipientName)
            recipient.setRecipientAvatar(recipientAvatar)
            composer.setSubject("")
            composer.setBody("")
            composer.setError(null)
            composer.setOpen(false)
            refreshAdminHistory(recipientGid)
        },
        openAdminMessageCompose = {
            if (recipient.anchorId == null || recipient.recipientGid == null) return@AdminActions
            composer.setError(null)
            composer.setOpen(true)
        },
        setAdminMessageSubject = { value -> composer.setSubject(value) },
        setAdminMessageBody = { value -> composer.setBody(value) },
        closeAdminUserCard = ::closeAdminUserCard,
        sendAdminMessageToCurrentRecipient = {
            val recipientGid = recipient.recipientGid ?: return@AdminActions
            dependencies.context.async.scope.launch {
                composer.setSending(true)
                composer.setError(null)
                val wasSent =
                    runCatching {
                        dependencies.adminMessagePublisher.send(
                            recipientGid,
                            composer.subject,
                            composer.body,
                        )
                    }.getOrDefault(false)
                composer.setSending(false)
                if (wasSent) {
                    composer.setSubject("")
                    composer.setBody("")
                    composer.setError(null)
                    composer.setOpen(false)
                    refreshAdminHistory(recipientGid)
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.messages.compose.success"),
                    )
                } else {
                    composer.setError(dependencies.context.async.translator.translate("connected.messages.compose.error"))
                }
            }
        },
        loadMoreAdminHistory = {
            val recipientGid = recipient.recipientGid ?: return@AdminActions
            if (history.isLoading || history.isAppending || !history.hasMore) {
                return@AdminActions
            }
            history.rememberAdminHistoryScrollTop()
            history.setAppending(true)
            history.setError(null)
            val nextPage = history.page + 1
            dependencies.context.async.scope.launch {
                val result = runCatching { dependencies.adminMessageHistoryService.fetch(recipientGid, page = nextPage) }.getOrNull()
                history.setAppending(false)
                if (result == null) {
                    history.setError(dependencies.context.async.translator.translate("connected.messages.actions.history.error"))
                } else {
                    applyAdminHistoryPage(result, append = true)
                }
            }
        },
    )
}
