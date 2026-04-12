package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

import straightway.mosa.client.ui.*

import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.Key
import react.Ref
import react.dom.events.ChangeEvent
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import straightway.mosa.client.i18n.Translator
import straightway.mosa.message.AdminMessage
import web.cssom.ClassName
import web.html.HTMLDivElement
import web.html.HTMLInputElement
import web.html.HTMLTextAreaElement

internal fun ChildrenBuilder.adminUserActionCardOverlay(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    admin: AdminController,
) = div {
    className = ClassName("admin-user-card-overlay")
    onClick = { event: MouseEvent<*, *> ->
        if (event.target == event.currentTarget) {
            admin.actions.closeAdminUserCard()
        }
    }
    adminUserActionCard(
        translator = translator,
        identityVisualsFactory = identityVisualsFactory,
        admin = admin,
    )
}

private fun ChildrenBuilder.adminUserActionCard(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    admin: AdminController,
) = div {
    val recipientAvatar = admin.state.adminMessageRecipientAvatar ?: return@div
    val recipientName = admin.state.adminMessageRecipientName ?: return@div
    className = ClassName("admin-user-card")
    onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
    div {
        className = ClassName("admin-user-card__header")
        div {
            className = ClassName("admin-user-card__identity")
            img {
                className = ClassName("admin-user-card__avatar")
                src = recipientAvatar
                alt = recipientName
            }
            div {
                className = ClassName("admin-user-card__identity-text")
                span {
                    className = ClassName("admin-user-card__eyebrow")
                    +translator.translate("connected.messages.actions.cardEyebrow")
                }
                span {
                    className = ClassName("admin-user-card__name")
                    +recipientName
                }
            }
        }
        button {
            className = ClassName("admin-user-card__close")
            ariaLabel = translator.translate("connected.messages.actions.close")
            onClick = { admin.actions.closeAdminUserCard() }
            +"×"
        }
    }
    if (!admin.state.isAdminMessageComposerOpen) {
        button {
            className = ClassName("button button--primary admin-user-card__primary-action")
            onClick = { admin.actions.openAdminMessageCompose() }
            +translator.translate("connected.messages.actions.send")
        }
    }
    adminModerationSection(
        translator = translator,
        admin = admin,
    )
    adminMessageHistorySection(
        AdminMessageHistorySectionProps(
            translator = translator,
            identityVisualsFactory = identityVisualsFactory,
            historyEntries = admin.state.adminHistoryEntries,
            isLoading = admin.state.isAdminHistoryLoading,
            isAppending = admin.state.isAdminHistoryAppending,
            errorText = admin.state.adminHistoryError,
            hasMore = admin.state.adminHistoryHasMore,
            listRef = admin.refs.adminHistoryListRef,
            onScrollToEnd = admin.actions.loadMoreAdminHistory,
        ),
    )
    if (admin.state.isAdminMessageComposerOpen) {
        adminMessageComposePopover(
            AdminMessageComposePopoverProps(
                translator = translator,
                recipientName = recipientName,
                subject = admin.state.adminMessageSubject,
                body = admin.state.adminMessageBody,
                isSending = admin.state.isAdminMessageSending,
                errorText = admin.state.adminMessageError,
                onSubjectChange = admin.actions.setAdminMessageSubject,
                onBodyChange = admin.actions.setAdminMessageBody,
                onClose = admin.actions.closeAdminUserCard,
                onSend = admin.actions.sendAdminMessageToCurrentRecipient,
            ),
        )
    }
}

private fun ChildrenBuilder.adminModerationSection(
    translator: Translator,
    admin: AdminController,
) = div {
    className = ClassName("admin-user-card__moderation")
    div {
        className = ClassName("admin-user-card__history-header")
        span {
            className = ClassName("admin-user-card__history-title")
            +translator.translate("connected.messages.actions.moderation.title")
        }
    }
    div {
        className = ClassName("admin-user-card__moderation-status")
        +when {
            admin.state.isRecipientCapabilitiesLoading ->
                translator.translate("connected.messages.actions.moderation.statusLoading")
            admin.state.isRecipientAdmin ->
                translator.translate("connected.messages.actions.moderation.statusProtectedAdmin")
            admin.state.isRecipientBlocked ->
                translator.translate("connected.messages.actions.moderation.statusBlocked")
            else ->
                translator.translate("connected.messages.actions.moderation.statusActive")
        }
    }
    if (admin.state.moderationError != null) {
        div {
            className = ClassName("admin-user-card__history-error")
            +admin.state.moderationError
        }
    }
    div {
        className = ClassName("admin-user-card__moderation-actions")
        if (!admin.state.isRecipientCapabilitiesLoading && !admin.state.isRecipientAdmin) {
            if (admin.state.isRecipientBlocked) {
                button {
                    className = ClassName("button button--secondary admin-user-card__moderation-button")
                    disabled = admin.state.isModerationRunning
                    onClick = { admin.actions.unblockCurrentRecipient() }
                    +translator.translate("connected.messages.actions.moderation.unblock")
                }
            } else {
                button {
                    className = ClassName("button button--secondary admin-user-card__moderation-button")
                    disabled = admin.state.isModerationRunning
                    onClick = { admin.actions.blockCurrentRecipient() }
                    +translator.translate("connected.messages.actions.moderation.block")
                }
            }
            button {
                className = ClassName("button button--secondary admin-user-card__moderation-button admin-user-card__moderation-delete")
                disabled = admin.state.isModerationRunning
                onClick = { admin.actions.deleteCurrentRecipient() }
                +translator.translate("connected.messages.actions.moderation.delete")
            }
        }
    }
}

private fun ChildrenBuilder.adminMessageHistorySection(
    props: AdminMessageHistorySectionProps,
) = div {
    className = ClassName("admin-user-card__history")
    div {
        className = ClassName("admin-user-card__history-header")
        span {
            className = ClassName("admin-user-card__history-title")
            +props.translator.translate("connected.messages.actions.history.title")
        }
    }
    div {
        ref = props.listRef
        className = ClassName("admin-user-card__history-list")
        onScroll = { event ->
            val target = event.currentTarget
            if (props.hasMore && isScrolledNearBottom(target)) {
                props.onScrollToEnd()
            }
        }
        when {
            props.isLoading ->
                div {
                    className = ClassName("admin-user-card__history-empty")
                    +props.translator.translate("connected.messages.actions.history.loading")
                }
            props.errorText != null ->
                div {
                    className = ClassName("admin-user-card__history-error")
                    +props.errorText
                }
            props.historyEntries.isEmpty() ->
                div {
                    className = ClassName("admin-user-card__history-empty")
                    +props.translator.translate("connected.messages.actions.history.empty")
                }
            else -> {
                props.historyEntries.forEach { entry ->
                    val senderVisuals = props.identityVisualsFactory.createFromGid(entry.senderGid)
                    val senderName = senderVisuals.name.toString().trim()
                    div {
                        key = entry.id.unsafeCast<Key>()
                        className = ClassName("admin-user-card__history-entry")
                        div {
                            className = ClassName("admin-user-card__history-meta")
                            div {
                                className = ClassName("admin-user-card__history-sender")
                                img {
                                    className = ClassName("admin-user-card__history-sender-avatar")
                                    src = senderVisuals.avatar
                                    alt = senderName
                                }
                                span {
                                    className = ClassName("admin-user-card__history-sender-name")
                                    +props.translator.translate(
                                        "connected.messages.inbox.from",
                                        mapOf("name" to senderName),
                                    )
                                }
                            }
                            span {
                                className = ClassName("admin-user-card__history-timestamp")
                                +formatTimestamp(entry.createdAt)
                            }
                        }
                        span {
                            className = ClassName("admin-user-card__history-subject")
                            +entry.subject
                        }
                        div {
                            className = ClassName("admin-user-card__history-body")
                            +entry.body
                        }
                        span {
                            className = ClassName("admin-user-card__history-lifetime")
                            +props.translator.translate(
                                "connected.messages.actions.history.lifetime",
                                mapOf("days" to entry.remainingLifetimeDays.toString()),
                            )
                        }
                    }
                }
                if (props.isAppending) {
                    div {
                        className = ClassName("admin-user-card__history-empty")
                        +props.translator.translate("connected.messages.actions.history.loadingMore")
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.adminMessageComposePopover(
    props: AdminMessageComposePopoverProps,
) = div {
    className = ClassName("message-compose")
    onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
    div {
        className = ClassName("message-compose__header")
        span {
            className = ClassName("message-compose__title")
            +props.translator.translate("connected.messages.compose.title", mapOf("name" to props.recipientName))
        }
        button {
            className = ClassName("message-compose__close")
            ariaLabel = props.translator.translate("connected.messages.compose.close")
            onClick = { props.onClose() }
            +"×"
        }
    }
    input {
        className = ClassName("message-compose__subject")
        placeholder = props.translator.translate("connected.messages.compose.subject")
        value = props.subject
        disabled = props.isSending
        onChange = { event: ChangeEvent<HTMLInputElement, HTMLInputElement> ->
            props.onSubjectChange(event.currentTarget.value)
        }
    }
    textarea {
        className = ClassName("message-compose__body")
        placeholder = props.translator.translate("connected.messages.compose.body")
        value = props.body
        disabled = props.isSending
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            props.onBodyChange(event.currentTarget.value)
        }
    }
    if (props.errorText != null) {
        div {
            className = ClassName("message-compose__error")
            +props.errorText
        }
    }
    div {
        className = ClassName("message-compose__actions")
        button {
            className = ClassName("button button--secondary message-compose__cancel")
            disabled = props.isSending
            onClick = { props.onClose() }
            +props.translator.translate("connected.messages.compose.cancel")
        }
        button {
            className = ClassName("button button--primary message-compose__send")
            disabled = props.subject.isBlank() || props.body.isBlank() || props.isSending
            onClick = { props.onSend() }
            +props.translator.translate("connected.messages.compose.send")
        }
    }
}
