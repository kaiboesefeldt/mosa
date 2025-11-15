package straightway.mosa.client.ui

import kotlinx.browser.document
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
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

external interface IdentityLandingViewProps : Props {
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
    var canUpload: Boolean
    var onConnect: () -> Unit
    var onPrevious: () -> Unit
    var onNext: () -> Unit
    var onDelete: () -> Unit
    var onSelect: (Int) -> Unit
    var onDownload: () -> Unit
    var onUpload: () -> Unit
    var onClose: (() -> Unit)?
}

val IdentityLandingView = FC<IdentityLandingViewProps> { props ->
    val translator = LandingViewInjector.get<Translator>()
    val totalItems = props.identityTotal.coerceAtLeast(1)
    val boundedIndex = props.identityIndex.coerceIn(0, totalItems - 1)
    val activeSegment = if (totalItems <= 1) 0 else boundedIndex
    val (areToolsVisible, setToolsVisible) = useState(false)
    val toolActions = IdentityToolsPresenter.present(props.toIdentityToolsCapabilities())

    useEffect(arrayOf(areToolsVisible)) {
        if (!areToolsVisible) return@useEffect
        val listener =
            EventListener { event: Event ->
                if (!event.target.isIdentityToolsTarget()) {
                    setToolsVisible(false)
                }
            }
        document.addEventListener("pointerdown", listener)
        { document.removeEventListener("pointerdown", listener) }
    }
    div {
        className = ClassName("landing-overlay")
        props.onClose?.let { onClose ->
            onClick = { event: MouseEvent<*, *> ->
                if (event.target == event.currentTarget) {
                    onClose()
                }
            }
        }
        div {
            className = ClassName("landing-dialog")
            identityDialogHeader(translator)
            identityCarousel(
                translator = translator,
                props = props,
                toolActions = toolActions,
                areToolsVisible = areToolsVisible,
                onToggleTools = { setToolsVisible { current -> !current } },
                onCloseTools = { setToolsVisible(false) },
                activeSegment = activeSegment,
                totalItems = totalItems,
            )
            props.errorText?.let { message ->
                div {
                    className = ClassName("landing-dialog__error")
                    +message
                }
            }
            div {
                className = ClassName("landing-dialog__actions")
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
        }
    }
}

private object LandingViewInjector : KoinComponent

private fun IdentityLandingViewProps.toIdentityToolsCapabilities(): IdentityToolsCapabilities =
    IdentityToolsCapabilities(
        canDelete = canDelete,
        canDownload = canDownload,
        canUpload = canUpload,
        onDelete = onDelete,
        onDownload = onDownload,
        onUpload = onUpload,
    )

internal fun IdentityLandingViewProps.applyFrom(screen: IdentityScreen.Landing) {
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
    canUpload = screen.canUpload
    onConnect = screen.onConnect
    onPrevious = screen.onPrevious
    onNext = screen.onNext
    onDelete = screen.onDelete
    onSelect = screen.onSelect
    onDownload = screen.onDownload
    onUpload = screen.onUpload
}
