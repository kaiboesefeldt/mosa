package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName

internal fun ChildrenBuilder.identityToolsMenu(
    translator: Translator,
    toolActions: List<IdentityToolAction>,
    isBusy: Boolean,
    areToolsVisible: Boolean,
    onToggleTools: () -> Unit,
    onCloseTools: () -> Unit,
) {
    div {
        className = identityToolsClass(areToolsVisible)
        button {
            className = ClassName("button button--icon landing-dialog__identity-tools-trigger")
            ariaLabel = translator.translate("button.identity.tools.aria")
            title = translator.translate("button.identity.tools.title")
            ariaExpanded = areToolsVisible
            onClick = { onToggleTools() }
            i {
                className = ClassName("fa fa-solid fa-ellipsis")
                ariaHidden = true
            }
        }
        if (areToolsVisible) {
            div {
                className = ClassName("landing-dialog__identity-tools-menu")
                toolActions.forEach { action ->
                    identityToolButton(
                        action = action,
                        translator = translator,
                        isBusy = isBusy,
                    ) {
                        onCloseTools()
                        action.onTrigger()
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.identityToolButton(
    action: IdentityToolAction,
    translator: Translator,
    isBusy: Boolean,
    onTriggered: () -> Unit,
) {
    val isDisabled = isBusy || !action.isEnabled
    button {
        className = ClassName("button button--icon landing-dialog__identity-tool")
        disabled = isDisabled
        ariaLabel = translator.translate(action.ariaKey)
        title = translator.translate(action.titleKey)
        onClick = {
            if (!isDisabled) onTriggered()
        }
        i {
            className = ClassName(action.iconClass)
            ariaHidden = true
        }
    }
}
