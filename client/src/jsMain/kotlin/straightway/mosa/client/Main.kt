package straightway.mosa.client

import kotlinx.browser.document
import kotlin.js.unsafeCast
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import straightway.mosa.client.assets.FontAwesomeResources
import straightway.mosa.client.ui.IdentityConnectedView
import straightway.mosa.client.ui.IdentityProfileView
import straightway.mosa.client.ui.applyFrom
import straightway.mosa.client.ui.IdentityScreen
import straightway.mosa.client.ui.PasswordPromptOverlay
import straightway.mosa.client.ui.useIdentitySession
import web.dom.Element as WebElement

fun main() {
    FontAwesomeResources.loadStyles()
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}

private val IdentityApp = FC<Props> {
    PasswordPromptOverlay {}
    when (val screen = useIdentitySession()) {
        IdentityScreen.Loading -> Unit
        is IdentityScreen.Connected ->
            IdentityConnectedView {
                identityName = screen.identityName
                profile = screen.profile
            }

        is IdentityScreen.Profile ->
            IdentityProfileView {
                applyFrom(screen)
            }
    }
}
