package straightway.mosa.client.ui

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.KeyboardEvent
import react.FC
import react.Props
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.identity.FictionalName
import web.cssom.ClassName
import web.clipboard.ClipboardEvent

external interface IdentityProfileViewProps : Props {
    var identityNameParts: FictionalName
    var identityAvatar: String?
    var isBusy: Boolean
    var errorText: String?
    var canDelete: Boolean
    var canNavigatePrevious: Boolean
    var canNavigateNext: Boolean
    var isShowingLastIdentity: Boolean
    var identityIndex: Int
    var identityTotal: Int
    var canConnect: Boolean
    var canDownload: Boolean
    var canCopy: Boolean
    var canUpload: Boolean
    var canPaste: Boolean
    var onConnect: () -> Unit
    var onPrevious: () -> Unit
    var onNext: () -> Unit
    var onDelete: () -> Unit
    var onSelect: (Int) -> Unit
    var onDownload: () -> Unit
    var onCopy: () -> Unit
    var onUpload: () -> Unit
    var onPaste: () -> Unit
    var onPasteContent: (String) -> Unit
    var onClose: (() -> Unit)?
}

val IdentityProfileView = FC<IdentityProfileViewProps> { props ->
    val translator = ProfileViewInjector.get<Translator>()
    val totalItems = props.identityTotal.coerceAtLeast(1)
    val boundedIndex = props.identityIndex.coerceIn(0, totalItems - 1)
    val activeSegment = if (totalItems <= 1) 0 else boundedIndex
    val (areToolsVisible, setToolsVisible) = useState(false)
    val (isCopyFeedbackVisible, setCopyFeedbackVisible) = useState(false)
    val toolActions = IdentityToolsPresenter.present(props.toIdentityToolsCapabilities())
    val toolActionsWithFeedback =
        toolActions.map { action ->
            if (action.ariaKey == "button.identity.copy.aria") {
                action.copy(onTrigger = {
                    action.onTrigger()
                    setCopyFeedbackVisible(true)
                })
            } else if (action.ariaKey == "button.identity.paste.aria") {
                action.copy(onTrigger = action.onTrigger)
            } else {
                action
            }
        }

    useEffect(arrayOf(areToolsVisible)) {
        if (!areToolsVisible) return@useEffect
        val listener =
            EventListener { event: Event ->
                if (!event.target.isIdentityToolsTarget()) {
                    setToolsVisible(false)
                }
            }
        document.addEventListener("pointerdown", listener)
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("pointerdown", listener)
        }
    }
    useEffect(arrayOf(isCopyFeedbackVisible)) {
        if (!isCopyFeedbackVisible) return@useEffect
        val timeoutId = window.setTimeout({ setCopyFeedbackVisible(false) }, 1800)
        try {
            awaitCancellation()
        } finally {
            window.clearTimeout(timeoutId)
        }
    }
    useEffect(arrayOf(props.onPasteContent, props.canPaste, props.isBusy)) {
        val listener: (Event) -> Unit = listener@ { event ->
            val pasteEvent = event.asPasteEvent() ?: return@listener
            if (props.isBusy || !props.canPaste) return@listener
            val content = pasteEvent.clipboardData?.getData("text/plain").orEmpty()
            if (content.isBlank()) return@listener
            pasteEvent.preventDefault()
            props.onPasteContent(content)
        }
        window.addEventListener("paste", listener)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("paste", listener)
        }
    }
    useEffect(arrayOf(props.onCopy, props.canCopy, props.isBusy)) {
        val listener: (Event) -> Unit = listener@ { event ->
            val keyboardEvent = event.asKeyboardEvent() ?: return@listener
            if (keyboardEvent.repeat || !keyboardEvent.isSystemCopyShortcut()) return@listener
            if (props.isBusy || !props.canCopy) return@listener
            keyboardEvent.preventDefault()
            props.onCopy()
            setCopyFeedbackVisible(true)
        }
        window.addEventListener("keydown", listener)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("keydown", listener)
        }
    }
    div {
        className = ClassName("profile-overlay")
        props.onClose?.let { onClose ->
            onClick = { event: MouseEvent<*, *> ->
                if (event.target == event.currentTarget) {
                    onClose()
                }
            }
        }
        div {
            className = ClassName("profile-dialog")
            identityDialogHeader(translator)
            identityCarousel(
                translator = translator,
                props = props,
                toolActions = toolActionsWithFeedback,
                areToolsVisible = areToolsVisible,
                onToggleTools = { setToolsVisible { current -> !current } },
                onCloseTools = { setToolsVisible(false) },
                activeSegment = activeSegment,
                totalItems = totalItems,
            )
            props.errorText?.let { message ->
                div {
                    className = ClassName("profile-dialog__error")
                    +message
                }
            }
            div {
                className = ClassName("profile-dialog__actions")
                button {
                    className = ClassName("button button--primary button--primary-large")
                    disabled = !props.canConnect
                    ariaLabel = translator.translate("button.connect.aria")
                    onClick = {
                        if (!props.isBusy) props.onConnect()
                    }
                    +translator.translate("button.connect.label")
                }
            }
            if (isCopyFeedbackVisible) {
                div {
                    className = ClassName("profile-dialog__toast")
                    +translator.translate("identity.copy.success")
                }
            }
        }
    }
}

private object ProfileViewInjector : KoinComponent

private fun IdentityProfileViewProps.toIdentityToolsCapabilities(): IdentityToolsCapabilities =
    IdentityToolsCapabilities(
        canDelete = canDelete,
        canDownload = canDownload,
        canCopy = canCopy,
        canUpload = canUpload,
        canPaste = canPaste,
        onDelete = onDelete,
        onDownload = onDownload,
        onCopy = onCopy,
        onUpload = onUpload,
        onPaste = onPaste,
    )

internal fun IdentityProfileViewProps.applyFrom(screen: IdentityScreen.Profile) {
    identityNameParts = screen.identityNameParts
    identityAvatar = screen.identityAvatar
    isBusy = screen.isBusy
    errorText = screen.errorText
    canDelete = screen.canDelete
    canNavigatePrevious = screen.canNavigatePrevious
    canNavigateNext = screen.canNavigateNext
    isShowingLastIdentity = screen.isShowingLastIdentity
    identityIndex = screen.identityIndex
    identityTotal = screen.identityTotal
    canConnect = screen.canConnect
    canDownload = screen.canDownload
    canCopy = screen.canCopy
    canUpload = screen.canUpload
    canPaste = screen.canPaste
    onConnect = screen.onConnect
    onPrevious = screen.onPrevious
    onNext = screen.onNext
    onDelete = screen.onDelete
    onSelect = screen.onSelect
    onDownload = screen.onDownload
    onCopy = screen.onCopy
    onUpload = screen.onUpload
    onPaste = screen.onPaste
    onPasteContent = screen.onPasteContent
}

private fun Event.asPasteEvent(): ClipboardEvent? =
    this as? ClipboardEvent

private fun Event.asKeyboardEvent(): KeyboardEvent? =
    this as? KeyboardEvent

private fun KeyboardEvent.isSystemCopyShortcut(): Boolean =
    (ctrlKey || metaKey) && !altKey && key.lowercase() == "c"
