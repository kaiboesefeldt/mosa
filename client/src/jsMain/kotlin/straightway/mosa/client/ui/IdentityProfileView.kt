package straightway.mosa.client.ui

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import kotlin.js.unsafeCast
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.KeyboardEvent
import react.ChildrenBuilder
import react.FC
import react.Props
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.useEffect
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.identity.FictionalName
import web.cssom.ClassName
import web.clipboard.ClipboardEvent
import web.html.ButtonType

external interface IdentityProfileViewProps : Props {
    var identityGid: String?
    var identityNameParts: FictionalName
    var identityAvatar: String?
    var isAdmin: Boolean
    var isBusy: Boolean
    var errorText: String?
    var statusText: String?
    var isPersisted: Boolean
    var canDelete: Boolean
    var deletesIdentityOnPod: Boolean
    var canDeletePosts: Boolean
    var canExportPosts: Boolean
    var canFreezePosts: Boolean
    var canRestorePosts: Boolean
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
    var onDeletePosts: () -> Unit
    var onExportPosts: () -> Unit
    var onFreezePosts: () -> Unit
    var onRestorePosts: () -> Unit
    var onSelect: (Int) -> Unit
    var onDownload: () -> Unit
    var onCopy: () -> Unit
    var onCopyGid: () -> Unit
    var onUpload: () -> Unit
    var onPaste: () -> Unit
    var onPasteContent: (String) -> Unit
    var onClose: (() -> Unit)?
    var exitAnimation: ProfileExitAnimation
    var entryAnimation: ProfileEntryAnimation
}

enum class ProfileExitAnimation {
    None,
    AvatarSuction,
}

enum class ProfileEntryAnimation {
    None,
    AvatarEmerge,
}

val IdentityProfileView = FC<IdentityProfileViewProps> { props ->
    val translator = ProfileViewInjector.get<Translator>()
    val totalItems = props.identityTotal.coerceAtLeast(1)
    val boundedIndex = props.identityIndex.coerceIn(0, totalItems - 1)
    val activeSegment = if (totalItems <= 1) 0 else boundedIndex
    val (areToolsVisible, setToolsVisible) = useState(false)
    val (isCopyFeedbackVisible, setCopyFeedbackVisible) = useState(false)
    val (copyFeedbackText, setCopyFeedbackText) = useState<String?>(null)
    val (isDeleteIdentityVisible, setDeleteIdentityVisible) = useState(false)
    val (isDeletePostsVisible, setDeletePostsVisible) = useState(false)
    val (isFreezePostsVisible, setFreezePostsVisible) = useState(false)
    val toolActions = IdentityToolsPresenter.present(props.toIdentityToolsCapabilities())
    val toolActionsWithFeedback =
        toolActions.map { action ->
            if (action.ariaKey == "button.identity.copy.aria") {
                action.copy(onTrigger = {
                    action.onTrigger()
                    setCopyFeedbackVisible(true)
                })
            } else if (action.ariaKey == "button.identity.delete.aria") {
                action.copy(onTrigger = {
                    setToolsVisible(false)
                    if (props.deletesIdentityOnPod) {
                        setDeleteIdentityVisible(true)
                    } else {
                        action.onTrigger()
                    }
                })
            } else if (action.ariaKey == "button.identity.delete-posts.aria") {
                action.copy(onTrigger = {
                    setToolsVisible(false)
                    setDeletePostsVisible(true)
                })
            } else if (action.ariaKey == "button.identity.freeze-posts.aria") {
                action.copy(onTrigger = {
                    setToolsVisible(false)
                    setFreezePostsVisible(true)
                })
            } else if (action.ariaKey == "button.identity.paste.aria") {
                action.copy(onTrigger = action.onTrigger)
            } else {
                action
            }
        }
    val toolGroups = IdentityToolsPresenter.presentGroups(toolActionsWithFeedback)

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
        val timeoutId = window.setTimeout({
            setCopyFeedbackVisible(false)
            setCopyFeedbackText(null)
        }, 1800)
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
            setCopyFeedbackText(translator.translate("identity.copy.success"))
            setCopyFeedbackVisible(true)
        }
        window.addEventListener("keydown", listener)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("keydown", listener)
        }
    }
    useEffect(arrayOf(props.onCopyGid, props.isBusy)) {
        val listener: (Event) -> Unit = listener@ { event ->
            val keyboardEvent = event.asKeyboardEvent() ?: return@listener
            if (keyboardEvent.repeat || !keyboardEvent.isCopyGidShortcut()) return@listener
            if (props.isBusy) return@listener
            keyboardEvent.preventDefault()
            props.onCopyGid()
            setCopyFeedbackText(translator.translate("identity.gid.copy.success"))
            setCopyFeedbackVisible(true)
        }
        window.addEventListener("keydown", listener)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("keydown", listener)
        }
    }
    val overlayClass =
        listOfNotNull(
            "profile-overlay",
            when (props.entryAnimation) {
                ProfileEntryAnimation.AvatarEmerge -> "profile-overlay--emerge"
                ProfileEntryAnimation.None -> null
            },
            when (props.exitAnimation) {
                ProfileExitAnimation.AvatarSuction -> "profile-overlay--suction"
                ProfileExitAnimation.None -> null
            },
        ).joinToString(" ")
    val dialogClass =
        listOfNotNull(
            "profile-dialog",
            when (props.entryAnimation) {
                ProfileEntryAnimation.AvatarEmerge -> "profile-dialog--emerge"
                ProfileEntryAnimation.None -> null
            },
            when (props.exitAnimation) {
                ProfileExitAnimation.AvatarSuction -> "profile-dialog--suction"
                ProfileExitAnimation.None -> null
            },
        ).joinToString(" ")
    div {
        className = ClassName(overlayClass)
        props.onClose
            ?.takeIf { props.exitAnimation == ProfileExitAnimation.None && props.entryAnimation == ProfileEntryAnimation.None }
            ?.let { onClose ->
                onClick = { event: MouseEvent<*, *> ->
                    if (event.target == event.currentTarget) {
                        onClose()
                    }
                }
            }
        div {
            className = ClassName(dialogClass)
            identityDialogHeader(translator)
            identityCarousel(
                translator = translator,
                props = props,
                toolGroups = toolGroups,
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
            props.statusText?.let { message ->
                div {
                    className = ClassName("profile-dialog__toast")
                    +message
                }
            }
            if (isCopyFeedbackVisible) {
                div {
                    className = ClassName("profile-dialog__toast")
                    +(copyFeedbackText ?: translator.translate("identity.copy.success"))
                }
            }
            if (isDeletePostsVisible) {
                val isDeleteDisabled = props.isBusy || !props.canDeletePosts
                val cancelLabel = translator.translate("identity.posts.delete.cancel")
                val confirmLabel = translator.translate("identity.posts.delete.confirm")
                confirmationSheet(
                    title = translator.translate("identity.posts.delete.title"),
                    body = translator.translate("identity.posts.delete.body"),
                    cancelLabel = cancelLabel,
                    confirmLabel = confirmLabel,
                    isConfirmDisabled = isDeleteDisabled,
                    confirmButtonClass = "button button--danger",
                    onCancel = { setDeletePostsVisible(false) },
                    onConfirm = {
                        setDeletePostsVisible(false)
                        props.onDeletePosts()
                    },
                )
            }
            if (isDeleteIdentityVisible) {
                val isDeleteDisabled = props.isBusy || !props.canDelete || !props.deletesIdentityOnPod
                confirmationSheet(
                    title = translator.translate("identity.delete.title"),
                    body = translator.translate("identity.delete.body"),
                    cancelLabel = translator.translate("identity.delete.cancel"),
                    confirmLabel = translator.translate("identity.delete.confirm"),
                    isConfirmDisabled = isDeleteDisabled,
                    confirmButtonClass = "button button--danger",
                    onCancel = { setDeleteIdentityVisible(false) },
                    onConfirm = {
                        setDeleteIdentityVisible(false)
                        props.onDelete()
                    },
                )
            }
            if (isFreezePostsVisible) {
                val isFreezeDisabled = props.isBusy || !props.canFreezePosts
                confirmationSheet(
                    title = translator.translate("identity.snapshot.freeze.title"),
                    body = translator.translate("identity.snapshot.freeze.body"),
                    cancelLabel = translator.translate("identity.snapshot.freeze.cancel"),
                    confirmLabel = translator.translate("identity.snapshot.freeze.confirm"),
                    isConfirmDisabled = isFreezeDisabled,
                    confirmButtonClass = "button button--danger",
                    onCancel = { setFreezePostsVisible(false) },
                    onConfirm = {
                        setFreezePostsVisible(false)
                        props.onFreezePosts()
                    },
                )
            }
        }
    }
}

private object ProfileViewInjector : KoinComponent

private fun IdentityProfileViewProps.toIdentityToolsCapabilities(): IdentityToolsCapabilities =
    IdentityToolsCapabilities(
        canDelete = canDelete,
        canDeletePosts = canDeletePosts,
        canExportPosts = canExportPosts,
        canFreezePosts = canFreezePosts,
        canRestorePosts = canRestorePosts,
        canDownload = canDownload,
        canCopy = canCopy,
        canUpload = canUpload,
        canPaste = canPaste,
        onDelete = onDelete,
        onDeletePosts = onDeletePosts,
        onExportPosts = onExportPosts,
        onFreezePosts = onFreezePosts,
        onRestorePosts = onRestorePosts,
        onDownload = onDownload,
        onCopy = onCopy,
        onUpload = onUpload,
        onPaste = onPaste,
    )

internal fun IdentityProfileViewProps.applyFrom(screen: IdentityScreen.Profile) {
    identityGid = screen.identityGid
    identityNameParts = screen.identityNameParts
    identityAvatar = screen.identityAvatar
    isAdmin = screen.isAdmin
    isBusy = screen.isBusy
    errorText = screen.errorText
    statusText = screen.statusText
    isPersisted = screen.isPersisted
    canDelete = screen.canDelete
    deletesIdentityOnPod = screen.deletesIdentityOnPod
    canDeletePosts = screen.canDeletePosts
    canExportPosts = screen.canExportPosts
    canFreezePosts = screen.canFreezePosts
    canRestorePosts = screen.canRestorePosts
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
    onDeletePosts = screen.onDeletePosts
    onExportPosts = screen.onExportPosts
    onFreezePosts = screen.onFreezePosts
    onRestorePosts = screen.onRestorePosts
    onSelect = screen.onSelect
    onDownload = screen.onDownload
    onCopy = screen.onCopy
    onCopyGid = screen.onCopyGid
    onUpload = screen.onUpload
    onPaste = screen.onPaste
    onPasteContent = screen.onPasteContent
    exitAnimation = ProfileExitAnimation.None
    entryAnimation = ProfileEntryAnimation.None
}

private fun Event.asPasteEvent(): ClipboardEvent? =
    unsafeCast<ClipboardEvent>()

private fun Event.asKeyboardEvent(): KeyboardEvent? =
    this as? KeyboardEvent

internal fun KeyboardEvent.isSystemCopyShortcut(): Boolean =
    (ctrlKey || metaKey) && !altKey && key.lowercase() == "c"

internal fun KeyboardEvent.isCopyGidShortcut(): Boolean =
    (ctrlKey || metaKey) && altKey && !shiftKey && key.lowercase() == "g"

private fun ChildrenBuilder.confirmationSheet(
    title: String,
    body: String,
    cancelLabel: String,
    confirmLabel: String,
    isConfirmDisabled: Boolean,
    confirmButtonClass: String,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    div {
        className = ClassName("profile-dialog__sheet-backdrop")
        onClick = { onCancel() }
        div {
            className = ClassName("profile-dialog__sheet")
            onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
            div {
                className = ClassName("profile-dialog__sheet-title")
                +title
            }
            div {
                className = ClassName("profile-dialog__sheet-content")
                +body
            }
            div {
                className = ClassName("profile-dialog__sheet-actions")
                ReactHTML.button {
                    className = ClassName("button button--secondary")
                    type = "button".unsafeCast<ButtonType>()
                    onClick = { onCancel() }
                    +cancelLabel
                }
                ReactHTML.button {
                    className = ClassName(confirmButtonClass)
                    type = "button".unsafeCast<ButtonType>()
                    disabled = isConfirmDisabled
                    onClick = {
                        if (!isConfirmDisabled) {
                            onConfirm()
                        }
                    }
                    +confirmLabel
                }
            }
        }
    }
}
