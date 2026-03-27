package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.ui.*

import org.w3c.dom.events.MouseEvent
import react.ChildrenBuilder
import react.dom.events.ChangeEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName
import web.html.HTMLTextAreaElement

internal fun ChildrenBuilder.replyComposerButton(
    label: String,
    disabled: Boolean,
    onActivate: () -> Unit,
) = button {
    className = ClassName("reply-composer-trigger")
    this.disabled = disabled
    ariaLabel = label
    title = label
    onClick = { onActivate() }
    i {
        className = ClassName("fa fa-solid fa-reply reply-composer-trigger__icon")
    }
}

internal fun ChildrenBuilder.replyComposerPopover(
    translator: Translator,
    text: String,
    isPublishing: Boolean,
    errorText: String?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) = div {
    className = ClassName("reply-composer")
    div {
        className = ClassName("reply-composer__header")
        span {
            className = ClassName("reply-composer__title")
            +translator.translate("connected.replies.compose.title")
        }
        button {
            className = ClassName("reply-composer__close")
            ariaLabel = translator.translate("connected.replies.compose.close")
            onClick = { onClose() }
            +"×"
        }
    }
    textarea {
        className = ClassName("reply-composer__textarea")
        placeholder = translator.translate("connected.replies.compose.placeholder")
        value = text
        disabled = isPublishing
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            onTextChange(event.currentTarget.value)
        }
    }
    if (errorText != null) {
        div {
            className = ClassName("reply-composer__error")
            +errorText
        }
    }
    div {
        className = ClassName("reply-composer__actions")
        button {
            className = ClassName("button button--primary reply-composer__send")
            disabled = text.trim().isEmpty() || isPublishing
            onClick = { onSend() }
            +translator.translate("connected.replies.compose.send")
        }
    }
}

internal fun ChildrenBuilder.complaintComposerPopover(
    props: ComplaintComposerPopoverProps,
) = div {
    className = ClassName("complaint-composer__panel")
    div {
        className = ClassName("complaint-composer__header")
        span {
            className = ClassName("complaint-composer__title")
            +props.title
        }
        button {
            className = ClassName("complaint-composer__close")
            ariaLabel = props.translator.translate("connected.complaints.compose.close")
            onClick = { props.onClose() }
            +"×"
        }
    }
    textarea {
        className = ClassName("complaint-composer__textarea")
        this.placeholder = props.placeholder
        value = props.text
        disabled = props.isSubmitting
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            props.onTextChange(event.currentTarget.value)
        }
    }
    if (props.errorText != null) {
        div {
            className = ClassName("complaint-composer__error")
            +props.errorText
        }
    }
    div {
        className = ClassName("complaint-composer__actions")
        button {
            className = ClassName("button button--secondary complaint-composer__cancel")
            disabled = props.isSubmitting
            onClick = { props.onClose() }
            +props.translator.translate("connected.complaints.compose.cancel")
        }
        button {
            className = ClassName("button button--primary complaint-composer__send")
            disabled = props.text.trim().isEmpty() || props.isSubmitting
            onClick = { props.onSend() }
            +props.sendLabel
        }
    }
}

internal fun ChildrenBuilder.floatingComposerButton(
    label: String,
    onActivate: () -> Unit,
) = button {
    className = ClassName("composer-button")
    ariaLabel = label
    title = label
    onClick = { onActivate() }
    +"+"
}

internal fun ChildrenBuilder.composerDialog(
    translator: Translator,
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    isPublishing: Boolean,
    errorText: String?,
) = div {
    className = ClassName("composer-overlay")
    div {
        className = ClassName("composer-dialog")
        div {
            className = ClassName("composer-dialog__header")
            span {
                className = ClassName("composer-dialog__title")
                +translator.translate("connected.composer.title")
            }
            button {
                className = ClassName("composer-dialog__close")
                ariaLabel = translator.translate("connected.composer.close")
                onClick = { onClose() }
                +"x"
            }
        }
        div {
            className = ClassName("composer-dialog__body")
            textarea {
                className = ClassName("composer-dialog__textarea")
                placeholder = translator.translate("connected.composer.placeholder")
                value = text
                disabled = isPublishing
                rows = 5
                onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
                    onTextChange(event.currentTarget.value)
                }
            }
            if (errorText != null) {
                div {
                    className = ClassName("composer-dialog__error")
                    +errorText
                }
            }
        }
        div {
            className = ClassName("composer-dialog__footer")
            button {
                className = ClassName("button button--primary composer-dialog__send")
                disabled = text.trim().isEmpty() || isPublishing
                onClick = { onSend() }
                +translator.translate("connected.composer.send")
            }
        }
    }
}
