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

internal fun identityToolActions(props: IdentityLandingViewProps): List<IdentityToolAction> =
    listOf(
        IdentityToolAction(
            isEnabled = props.canDelete,
            ariaKey = "button.identity.delete.aria",
            titleKey = "button.identity.delete.aria",
            iconClass = "fa fa-solid fa-trash-can",
            onTrigger = props.onDelete,
        ),
        IdentityToolAction(
            isEnabled = props.canDownload,
            ariaKey = "button.identity.download.aria",
            titleKey = "button.identity.download.label",
            iconClass = "fa fa-solid fa-download",
            onTrigger = props.onDownload,
        ),
        IdentityToolAction(
            isEnabled = props.canUpload,
            ariaKey = "button.identity.upload.aria",
            titleKey = "button.identity.upload.label",
            iconClass = "fa fa-solid fa-upload",
            onTrigger = props.onUpload,
        ),
    )

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

internal data class IdentityToolAction(
    val isEnabled: Boolean,
    val ariaKey: String,
    val titleKey: String,
    val iconClass: String,
    val onTrigger: () -> Unit,
)
