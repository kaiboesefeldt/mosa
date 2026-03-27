package straightway.mosa.client

import kotlinx.browser.document
import kotlin.js.unsafeCast
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import straightway.mosa.client.assets.FontAwesomeResources
import web.dom.Element as WebElement

@ExperimentalWasmJsInterop
fun main() {
    FontAwesomeResources.loadStyles()
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}
