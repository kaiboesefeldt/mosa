package straightway.mosa.client.ui.identityconnected.shell

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.ui.*

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedShellLayout(
    translator: straightway.mosa.client.i18n.Translator,
    headerProps: HeaderProps,
    boardProps: BoardProps,
) = div {
    className = ClassName("connected-shell")
    connectedShellHeader(translator, headerProps)
    connectedContentBoard(translator, boardProps)
}
