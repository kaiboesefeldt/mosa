package straightway.mosa.client.ui.identityconnected

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
    admin: IdentityConnectedViewAdminController,
) = div {
    className = ClassName("admin-user-card-overlay")
    onClick = { event: MouseEvent<*, *> ->
        if (event.target == event.currentTarget) {
            admin.closeAdminUserCard()
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
    admin: IdentityConnectedViewAdminController,
) = div {
    val recipientAvatar = admin.adminMessageRecipientAvatar ?: return@div
    val recipientName = admin.adminMessageRecipientName ?: return@div
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
            onClick = { admin.closeAdminUserCard() }
            +"×"
        }
    }
    if (!admin.isAdminMessageComposerOpen) {
        button {
            className = ClassName("button button--primary admin-user-card__primary-action")
            onClick = { admin.openAdminMessageCompose() }
            +translator.translate("connected.messages.actions.send")
        }
    }
    adminMessageHistorySection(
        AdminMessageHistorySectionProps(
            translator = translator,
            identityVisualsFactory = identityVisualsFactory,
            historyEntries = admin.adminHistoryEntries,
            isLoading = admin.isAdminHistoryLoading,
            isAppending = admin.isAdminHistoryAppending,
            errorText = admin.adminHistoryError,
            hasMore = admin.adminHistoryHasMore,
            listRef = admin.adminHistoryListRef,
            onScrollToEnd = admin.loadMoreAdminHistory,
        ),
    )
    if (admin.isAdminMessageComposerOpen) {
        adminMessageComposePopover(
            AdminMessageComposePopoverProps(
                translator = translator,
                recipientName = recipientName,
                subject = admin.adminMessageSubject,
                body = admin.adminMessageBody,
                isSending = admin.isAdminMessageSending,
                errorText = admin.adminMessageError,
                onSubjectChange = admin.setAdminMessageSubject,
                onBodyChange = admin.setAdminMessageBody,
                onClose = admin.closeAdminUserCard,
                onSend = admin.sendAdminMessageToCurrentRecipient,
            ),
        )
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
