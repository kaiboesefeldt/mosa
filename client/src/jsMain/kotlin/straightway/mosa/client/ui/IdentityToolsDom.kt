package straightway.mosa.client.ui

import org.w3c.dom.Element
import org.w3c.dom.events.EventTarget
import web.cssom.ClassName

private const val identityToolsContainerClassName = "profile-dialog__identity-tools"
private const val identityToolsExpandedClassName = "profile-dialog__identity-tools--expanded"

internal fun identityToolsClass(isExpanded: Boolean): ClassName =
    if (isExpanded) {
        ClassName("$identityToolsContainerClassName $identityToolsExpandedClassName")
    } else {
        ClassName(identityToolsContainerClassName)
    }

internal fun EventTarget?.isIdentityToolsTarget(): Boolean {
    var element = this as? Element ?: return false
    while (true) {
        if (element.classList.contains(identityToolsContainerClassName)) {
            return true
        }
        element = element.parentElement ?: break
    }
    return false
}
