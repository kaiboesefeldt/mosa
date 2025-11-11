package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import straightway.mosa.client.i18n.Translator
import straightway.mosa.identity.FictionalName
import web.cssom.ClassName
import web.html.HTMLDivElement

internal fun ChildrenBuilder.identityCarousel(
    translator: Translator,
    props: IdentityLandingViewProps,
    toolActions: List<IdentityToolAction>,
    areToolsVisible: Boolean,
    onToggleTools: () -> Unit,
    onCloseTools: () -> Unit,
    activeSegment: Int,
    totalItems: Int,
) {
    div {
        className = ClassName("landing-dialog__identity")
        identityToolsMenu(
            translator = translator,
            toolActions = toolActions,
            isBusy = props.isBusy,
            areToolsVisible = areToolsVisible,
            onToggleTools = onToggleTools,
            onCloseTools = onCloseTools,
        )
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
                props.identityNameParts.nameLines.forEachIndexed { index, line ->
                    if (index != 0) {
                        br { }
                    }
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
                    className = identityProgressSegmentClass(segmentIndex == activeSegment)
                }
            }
        }
    }
}

private fun identityProgressSegmentClass(isActive: Boolean): ClassName =
    if (isActive) {
        ClassName("landing-dialog__identity-progress-segment landing-dialog__identity-progress-segment--active")
    } else {
        ClassName("landing-dialog__identity-progress-segment")
    }

private val FictionalName.nameLines: List<String>
    get() {
        val lines = mutableListOf<String>()
        val givenNameLine = givenNames.filter { it.isNotBlank() }.joinToString(" ")
        if (givenNameLine.isNotBlank()) {
            lines += givenNameLine
        }
        val surnameParts = mutableListOf<String>()
        addendum?.takeIf { it.isNotBlank() }?.let { surnameParts += "$it'" }
        if (sureName.isNotBlank()) {
            surnameParts += sureName
        }
        if (surnameParts.isNotEmpty()) {
            lines += surnameParts.joinToString(" ")
        }
        if (lines.isEmpty()) {
            lines += ""
        }
        return lines
    }
