package straightway.mosa.client.ui.identityconnected

import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.Key
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import web.cssom.ClassName

internal fun ChildrenBuilder.adminMessageInboxPopover(
    props: AdminMessageInboxPopoverProps,
) = div {
    ref = props.popoverRef
    className = ClassName("message-inbox")
    onScroll = { event ->
        val target = event.currentTarget
        if (props.hasMore && isScrolledNearBottom(target)) {
            props.onScrollToEnd()
        }
    }
    div {
        className = ClassName("message-inbox__header")
        span {
            className = ClassName("message-inbox__title")
            +props.translator.translate("connected.messages.inbox.title")
        }
        button {
            className =
                if (props.showSeenMessages) {
                    ClassName("message-inbox__filter message-inbox__filter--active")
                } else {
                    ClassName("message-inbox__filter")
                }
            ariaLabel =
                props.translator.translate(
                    if (props.showSeenMessages) {
                        "connected.messages.inbox.hideRead"
                    } else {
                        "connected.messages.inbox.showRead"
                    },
                )
            onClick = { props.onToggleSeenVisibility() }
            +props.translator.translate(
                if (props.showSeenMessages) {
                    "connected.messages.inbox.hideRead"
                } else {
                    "connected.messages.inbox.showRead"
                },
            )
        }
        button {
            className = ClassName("message-inbox__close")
            ariaLabel = props.translator.translate("connected.messages.inbox.close")
            onClick = { props.onClose() }
            +"×"
        }
    }
    when {
        props.entries.isEmpty() && props.isLoading ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate("connected.messages.inbox.loading")
            }
        props.entries.isEmpty() && props.errorText != null ->
            div {
                className = ClassName("message-inbox__error")
                +props.errorText
            }
        props.entries.isEmpty() ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate(
                    if (!props.showSeenMessages && props.hasHiddenSeenEntries) {
                        "connected.messages.inbox.emptyUnread"
                    } else {
                        "connected.messages.inbox.empty"
                    },
                )
            }
        else -> {
            val readIds = props.readMessageIds.toSet()
            props.entries.forEach { entry ->
                val senderVisuals = props.identityVisualsFactory.createFromGid(entry.senderGid)
                val senderName = senderVisuals.name.toString().trim()
                div {
                    key = entry.id.unsafeCast<Key>()
                    className =
                        ClassName(
                            if (entry.id in readIds) {
                                "message-inbox__entry message-inbox__entry--read"
                            } else {
                                "message-inbox__entry"
                            },
                        )
                    div {
                        className = ClassName("message-inbox__sender")
                        img {
                            className = ClassName("message-inbox__sender-avatar")
                            src = senderVisuals.avatar
                            alt = senderName
                        }
                        div {
                            className = ClassName("message-inbox__sender-details")
                            span {
                                className = ClassName("message-inbox__sender-name")
                                +props.translator.translate(
                                    "connected.messages.inbox.from",
                                    mapOf("name" to senderName),
                                )
                            }
                            span {
                                className = ClassName("message-inbox__timestamp")
                                +formatTimestamp(entry.createdAt)
                            }
                        }
                    }
                    span {
                        className = ClassName("message-inbox__subject")
                        +entry.subject
                    }
                    if (props.showSeenMessages && props.hasReadEntries && entry.id in readIds) {
                        span {
                            className = ClassName("message-inbox__status")
                            +props.translator.translate("connected.messages.inbox.read")
                        }
                    }
                    div {
                        className = ClassName("message-inbox__body")
                        +entry.body
                    }
                }
            }
            if (props.errorText != null) {
                div {
                    className = ClassName("message-inbox__error")
                    +props.errorText
                }
            } else if (props.isLoading || props.isAppending) {
                div {
                    className = ClassName("message-inbox__meta")
                    +props.translator.translate(
                        if (props.isAppending) {
                            "connected.messages.inbox.loadingMore"
                        } else {
                            "connected.messages.inbox.loading"
                        },
                    )
                }
            }
        }
    }
}
