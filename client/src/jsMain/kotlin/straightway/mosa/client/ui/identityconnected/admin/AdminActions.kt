package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

import straightway.mosa.client.ui.*

import kotlinx.coroutines.launch
import kotlinx.browser.window
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
    val blockCurrentRecipient: () -> Unit,
    val unblockCurrentRecipient: () -> Unit,
    val deleteCurrentRecipient: () -> Unit,
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
        if (composer.isSending || recipient.isModerationRunning) return
        recipient.setAnchorId(null)
        recipient.setRecipientGid(null)
        recipient.setRecipientName(null)
        recipient.setRecipientAvatar(null)
        recipient.setBlocked(false)
        recipient.setAdmin(false)
        recipient.setLoadingCapabilities(false)
        recipient.setModerationRunning(false)
        recipient.setModerationError(null)
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

    fun refreshModerationState(recipientGid: String) {
        recipient.setLoadingCapabilities(true)
        recipient.setModerationError(null)
        dependencies.context.async.scope.launch {
            val result = runCatching { dependencies.adminIdentityModerationStatusService.fetch(recipientGid) }.getOrNull()
            recipient.setLoadingCapabilities(false)
            if (result == null) {
                recipient.setBlocked(false)
                recipient.setAdmin(false)
                recipient.setModerationError(
                    dependencies.context.async.translator.translate("connected.messages.actions.moderation.statusError"),
                )
            } else {
                recipient.setAdmin(result.isAdmin)
                recipient.setBlocked(result.isBlocked)
            }
        }
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
            recipient.setBlocked(false)
            recipient.setAdmin(false)
            recipient.setLoadingCapabilities(true)
            recipient.setModerationRunning(false)
            recipient.setModerationError(null)
            refreshAdminHistory(recipientGid)
            refreshModerationState(recipientGid)
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
        blockCurrentRecipient = {
            val recipientGid = recipient.recipientGid ?: return@AdminActions
            if (
                recipient.isLoadingCapabilities ||
                recipient.isModerationRunning ||
                recipient.isBlocked ||
                recipient.isAdmin ||
                recipientGid == dependencies.connectedIdentityGid
            ) {
                return@AdminActions
            }
            dependencies.context.async.scope.launch {
                recipient.setModerationRunning(true)
                recipient.setModerationError(null)
                val blocked = dependencies.identityModerationService.block(recipientGid)
                recipient.setModerationRunning(false)
                if (blocked) {
                    recipient.setBlocked(true)
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.blockSuccess"),
                    )
                } else {
                    recipient.setModerationError(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.error"),
                    )
                }
            }
        },
        unblockCurrentRecipient = {
            val recipientGid = recipient.recipientGid ?: return@AdminActions
            if (
                recipient.isLoadingCapabilities ||
                recipient.isModerationRunning ||
                !recipient.isBlocked ||
                recipient.isAdmin ||
                recipientGid == dependencies.connectedIdentityGid
            ) {
                return@AdminActions
            }
            dependencies.context.async.scope.launch {
                recipient.setModerationRunning(true)
                recipient.setModerationError(null)
                val unblocked = dependencies.identityModerationService.unblock(recipientGid)
                recipient.setModerationRunning(false)
                if (unblocked) {
                    recipient.setBlocked(false)
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.unblockSuccess"),
                    )
                } else {
                    recipient.setModerationError(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.error"),
                    )
                }
            }
        },
        deleteCurrentRecipient = {
            val recipientGid = recipient.recipientGid ?: return@AdminActions
            if (
                recipient.isLoadingCapabilities ||
                recipient.isModerationRunning ||
                recipient.isAdmin ||
                recipientGid == dependencies.connectedIdentityGid
            ) {
                return@AdminActions
            }
            val confirmed =
                window.confirm(
                    dependencies.context.async.translator.translate(
                        "connected.messages.actions.moderation.deleteConfirm",
                    ),
                )
            if (!confirmed) return@AdminActions
            dependencies.context.async.scope.launch {
                recipient.setModerationRunning(true)
                recipient.setModerationError(null)
                val deleted = dependencies.identityModerationService.delete(recipientGid)
                recipient.setModerationRunning(false)
                if (deleted) {
                    dependencies.context.showToast(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.deleteSuccess"),
                    )
                    closeAdminUserCard()
                } else {
                    recipient.setModerationError(
                        dependencies.context.async.translator.translate("connected.messages.actions.moderation.error"),
                    )
                }
            }
        },
    )
}
