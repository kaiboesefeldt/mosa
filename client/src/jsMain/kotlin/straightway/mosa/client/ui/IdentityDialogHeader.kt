package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName

internal fun ChildrenBuilder.identityDialogHeader(translator: Translator) {
    div {
        className = ClassName("profile-dialog__header")
        h1 {
            className = ClassName("profile-dialog__title")
            +translator.translate("profile.title")
        }
        p {
            className = ClassName("profile-dialog__description")
            +translator.translate("profile.description")
        }
    }
}
