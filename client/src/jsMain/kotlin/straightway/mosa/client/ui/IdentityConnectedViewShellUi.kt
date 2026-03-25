package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedShellLayout(
    translator: straightway.mosa.client.i18n.Translator,
    headerProps: IdentityConnectedViewHeaderProps,
    boardProps: IdentityConnectedViewBoardProps,
) = div {
    className = ClassName("connected-shell")
    connectedShellHeader(translator, headerProps)
    connectedContentBoard(translator, boardProps)
}
