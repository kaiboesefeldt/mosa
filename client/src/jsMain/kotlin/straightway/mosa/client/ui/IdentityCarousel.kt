package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import straightway.mosa.client.i18n.Translator
import straightway.mosa.identity.FictionalName
import web.cssom.ClassName
import web.html.ButtonType
import web.html.HTMLDivElement

internal fun ChildrenBuilder.identityCarousel(
    translator: Translator,
    props: IdentityProfileViewProps,
    toolActions: List<IdentityToolAction>,
    areToolsVisible: Boolean,
    onToggleTools: () -> Unit,
    onCloseTools: () -> Unit,
    activeSegment: Int,
    totalItems: Int,
) {
    val navigationPresenter = IdentityCarouselNavigationPresenter(
        props = props,
        activeSegment = activeSegment,
    )
    val previousNav = navigationPresenter.previousButton()
    val nextNav = navigationPresenter.nextButton()
    div {
        className = identityContainerClass(props.isPersisted)
        identityToolsMenu(
            translator = translator,
            toolActions = toolActions,
            isBusy = props.isBusy,
            areToolsVisible = areToolsVisible,
            onToggleTools = onToggleTools,
            onCloseTools = onCloseTools,
        )
        button {
            className = ClassName("profile-dialog__nav-button")
            disabled = previousNav.isDisabled
            ariaLabel = translator.translate("button.identity.previous.aria")
            title = translator.translate("button.identity.previous.aria")
            onClick = { previousNav.onClick() }
            i {
                className = ClassName(previousNav.icon.cssClass)
                ariaHidden = true
            }
        }
        div {
            className = ClassName("profile-dialog__identity-content")
            val avatarClass =
                if (props.isPersisted) {
                    "profile-dialog__identity-avatar"
                } else {
                    "profile-dialog__identity-avatar profile-dialog__identity-avatar--transient"
                }
            span {
                className = ClassName("profile-dialog__identity-heading")
                span {
                    className = ClassName("profile-dialog__identity-heading-label")
                    +translator.translate("profile.identityLabel")
                }
            }
            ReactHTML.button {
                className = ClassName("profile-dialog__identity-avatar-button")
                disabled = !props.canConnect
                ariaLabel = translator.translate("profile.identityAvatar.connect")
                title = translator.translate("profile.identityAvatar.tooltip")
                onClick = {
                    if (!props.isBusy && props.canConnect) {
                        props.onConnect()
                    }
                }
                val avatarUri = props.identityAvatar
                if (avatarUri != null) {
                    img {
                        className = ClassName(avatarClass)
                        src = avatarUri
                        alt = props.identityNameParts.toString()
                    }
                } else {
                    div {
                        className = ClassName("$avatarClass profile-dialog__identity-avatar--placeholder")
                        +props.identityNameParts.placeholderInitial()
                    }
                }
            }
            p {
                className = ClassName("profile-dialog__identity-value")
                props.identityNameParts.nameLines.forEachIndexed { index, line ->
                    if (index != 0) {
                        br { }
                    }
                    +line
                }
            }
        }
        button {
            className = ClassName("profile-dialog__nav-button")
            disabled = nextNav.isDisabled
            ariaLabel = translator.translate("button.identity.next.aria")
            title = translator.translate("button.identity.next.aria")
            onClick = { nextNav.onClick() }
            i {
                className = ClassName(nextNav.icon.cssClass)
                ariaHidden = true
            }
        }
        div {
            className = ClassName("profile-dialog__identity-progress")
            onClick = { event: MouseEvent<HTMLDivElement, *> ->
                val rect = event.currentTarget.getBoundingClientRect()
                val width = rect.width
                val offset = event.clientX - rect.left
                navigationPresenter.progressSelection(width, offset)?.let { target ->
                    props.onSelect(target)
                }
            }
            repeat(totalItems) { segmentIndex ->
                span {
                    className = identityProgressSegmentClass(navigationPresenter.isSegmentActive(segmentIndex))
                }
            }
        }
    }
}

private fun identityProgressSegmentClass(isActive: Boolean): ClassName =
    if (isActive) {
        ClassName("profile-dialog__identity-progress-segment profile-dialog__identity-progress-segment--active")
    } else {
        ClassName("profile-dialog__identity-progress-segment")
    }

private fun identityContainerClass(isPersisted: Boolean): ClassName =
    if (isPersisted) {
        ClassName("profile-dialog__identity")
    } else {
        ClassName("profile-dialog__identity profile-dialog__identity--transient")
    }

private fun FictionalName.placeholderInitial(): String =
    listOf(givenNames.firstOrNull(), sureName.takeIf { it.isNotBlank() })
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.firstOrNull()
        ?.uppercaseChar()
        ?.toString()
        ?: "?"

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
