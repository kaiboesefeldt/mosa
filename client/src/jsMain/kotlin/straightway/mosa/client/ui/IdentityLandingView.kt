package straightway.mosa.client.ui

import kotlinx.browser.document
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.useEffect
import react.useState
import straightway.mosa.client.i18n.Translator
import straightway.mosa.identity.FictionalName
import web.cssom.ClassName
import web.html.HTMLDivElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener

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
}

val IdentityLandingView = FC<IdentityLandingViewProps> { props ->
    val translator = LandingViewInjector.get<Translator>()
    val totalItems = props.identityTotal.coerceAtLeast(1)
    val boundedIndex = props.identityIndex.coerceIn(0, totalItems - 1)
    val activeSegment = if (totalItems <= 1) 0 else boundedIndex
    val (areToolsVisible, setToolsVisible) = useState(false)

    useEffect(arrayOf(areToolsVisible)) {
        if (!areToolsVisible) return@useEffect
        val listener =
            EventListener { event: Event ->
                val target = event.target ?: return@EventListener
                val closestTools = target.asDynamic().closest?.call(target, ".landing-dialog__identity-tools")
                if (closestTools == null) {
                    setToolsVisible(false)
                }
            }
        document.addEventListener("pointerdown", listener)
        { document.removeEventListener("pointerdown", listener) }
    }
    div {
        className = ClassName("landing-overlay")
        div {
            className = ClassName("landing-dialog")
            div {
                className = ClassName("landing-dialog__header")
                h1 {
                    className = ClassName("landing-dialog__title")
                    +translator.translate("landing.title")
                }
                p {
                    className = ClassName("landing-dialog__description")
                    +translator.translate("landing.description")
                }
            }
            div {
                className = ClassName("landing-dialog__identity")
                div {
                    className =
                        ClassName(
                            buildString {
                                append("landing-dialog__identity-tools")
                                if (areToolsVisible) append(" landing-dialog__identity-tools--expanded")
                            }
                        )
                    button {
                        className = ClassName("button button--icon landing-dialog__identity-tools-trigger")
                        ariaLabel = translator.translate("button.identity.tools.aria")
                        title = translator.translate("button.identity.tools.title")
                        ariaExpanded = areToolsVisible
                        onClick = {
                            setToolsVisible { current -> !current }
                        }
                        i {
                            className = ClassName("fa fa-solid fa-ellipsis")
                            ariaHidden = true
                        }
                    }
                    if (areToolsVisible) {
                        div {
                            className = ClassName("landing-dialog__identity-tools-menu")
                            button {
                                className = ClassName("button button--icon landing-dialog__identity-tool")
                                disabled = props.isBusy || !props.canDelete
                                ariaLabel = translator.translate("button.identity.delete.aria")
                                title = translator.translate("button.identity.delete.aria")
                                onClick = {
                                    if (!props.isBusy && props.canDelete) {
                                        setToolsVisible(false)
                                        props.onDelete()
                                    }
                                }
                                i {
                                    className = ClassName("fa fa-solid fa-trash-can")
                                    ariaHidden = true
                                }
                            }
                            button {
                                className = ClassName("button button--icon landing-dialog__identity-tool")
                                disabled = props.isBusy || !props.canDownload
                                ariaLabel = translator.translate("button.identity.download.aria")
                                title = translator.translate("button.identity.download.label")
                                onClick = {
                                    if (!props.isBusy && props.canDownload) {
                                        setToolsVisible(false)
                                        props.onDownload()
                                    }
                                }
                                i {
                                    className = ClassName("fa fa-solid fa-download")
                                    ariaHidden = true
                                }
                            }
                            button {
                                className = ClassName("button button--icon landing-dialog__identity-tool")
                                disabled = props.isBusy || !props.canUpload
                                ariaLabel = translator.translate("button.identity.upload.aria")
                                title = translator.translate("button.identity.upload.label")
                                onClick = {
                                    if (!props.isBusy && props.canUpload) {
                                        setToolsVisible(false)
                                        props.onUpload()
                                    }
                                }
                                i {
                                    className = ClassName("fa fa-solid fa-upload")
                                    ariaHidden = true
                                }
                            }
                        }
                    }
                }
                button {
                    className = ClassName("landing-dialog__nav-button")
                    disabled = props.isBusy || !props.canNavigatePrevious
                    ariaLabel = translator.translate("button.identity.previous.aria")
                    title = translator.translate("button.identity.previous.aria")
                    onClick = { props.onPrevious() }
                    i {
                        className = ClassName("fa fa-solid fa-chevron-left")
                        ariaHidden = true
                    }
                }
                div {
                    className = ClassName("landing-dialog__identity-content")
                    span {
                        className = ClassName("landing-dialog__identity-heading")
                        span {
                            className = ClassName("landing-dialog__identity-heading-label")
                            +translator.translate("landing.identityLabel")
                        }
                    }
                    p {
                        className = ClassName("landing-dialog__identity-value")
                        props.identityAvatar?.let { avatarUri ->
                            div {
                                img {
                                    className = ClassName("landing-dialog__identity-avatar")
                                    src = avatarUri
                                    alt = props.identityNameParts.toString()
                                }
                            }
                        }
                        props.identityNameParts.nameLines.forEachIndexed { i, line ->
                            if (i != 0) br { }
                            +line
                        }
                    }
                }
                button {
                    className = ClassName("landing-dialog__nav-button")
                    disabled = props.isBusy || !props.canNavigateNext
                    ariaLabel = translator.translate("button.identity.next.aria")
                    title = translator.translate("button.identity.next.aria")
                    onClick = { props.onNext() }
                    if (props.isShowingLastIdentity) {
                        i {
                            className = ClassName("fa fa-solid fa-user-plus")
                            ariaHidden = true
                        }
                    } else {
                        i {
                            className = ClassName("fa fa-solid fa-chevron-right")
                            ariaHidden = true
                        }
                    }
                }
                div {
                    className = ClassName("landing-dialog__identity-progress")
                    onClick = { event: MouseEvent<HTMLDivElement, *> ->
                        val segments = props.identityTotal
                        if (!props.isBusy && segments > 0) {
                            val rect = event.currentTarget.getBoundingClientRect()
                            val width = rect.width
                            if (width > 0.0) {
                                val offset = (event.clientX - rect.left).coerceIn(0.0, width)
                                val rawIndex = ((offset / width) * segments).toInt()
                                val targetIndex = rawIndex.coerceIn(0, segments - 1)
                                props.onSelect(targetIndex)
                            }
                        }
                    }
                    repeat(totalItems) { segmentIndex ->
                        span {
                            className =
                                ClassName(
                                    buildString {
                                        append("landing-dialog__identity-progress-segment")
                                        if (segmentIndex == activeSegment) {
                                            append(" landing-dialog__identity-progress-segment--active")
                                        }
                                    }
                                )
                        }
                    }
                }
            }
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

private val FictionalName.nameLines: List<String> get() = listOf(
    givenNames.joinToString(" ").trim(),
    addendum?.let { "$it'"} + sureName
)
