package straightway.mosa.client

import kotlinx.browser.document
import kotlin.js.unsafeCast
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import straightway.mosa.client.ui.IdentityConnectedView
import straightway.mosa.client.ui.IdentityLandingView
import straightway.mosa.client.ui.IdentityScreen
import straightway.mosa.client.ui.useIdentitySession
import web.dom.Element as WebElement

fun main() {
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}

private val IdentityApp = FC<Props> {
    when (val screen = useIdentitySession()) {
        is IdentityScreen.Connected ->
            IdentityConnectedView {
                identityName = screen.identityName
                misJwt = screen.misJwt
                queryPreview = screen.queryPreview
            }

        is IdentityScreen.Landing ->
            IdentityLandingView {
                identityNameParts = screen.identityNameParts
                identityAvatar = screen.identityAvatar
                isBusy = screen.isBusy
                errorText = screen.errorText
                canDelete = screen.canDelete
                canNavigatePrevious = screen.canNavigatePrevious
                canNavigateNext = screen.canNavigateNext
                isShowingLastIdentity = screen.isShowingLastIdentity
                identityIndex = screen.identityIndex
                identityTotal = screen.identityTotal
                canConnect = screen.canConnect
                canDownload = screen.canDownload
                onConnect = screen.onConnect
                onPrevious = screen.onPrevious
                onNext = screen.onNext
                onDelete = screen.onDelete
                onSelect = screen.onSelect
                onDownload = screen.onDownload
            }
    }
}
