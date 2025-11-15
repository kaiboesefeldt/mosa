package straightway.mosa.client.ui

import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.render
import straightway.mosa.identity.FictionalName

internal fun renderIdentityLandingView(
    configure: IdentityLandingViewProps.() -> Unit = {},
): RenderResult =
    render(
        IdentityLandingView.create {
            AppModule.start()
            identityNameParts = FictionalName(
                givenNames = listOf("Default"),
                sureName = "Identity",
            )
            identityAvatar = null
            isBusy = false
            errorText = null
            canDelete = true
            canConnect = true
            canNavigatePrevious = false
            canNavigateNext = false
            isShowingLastIdentity = false
            identityIndex = 0
            identityTotal = 1
            onConnect = {}
            canDownload = true
            onPrevious = {}
            onNext = {}
            onDelete = {}
            onDownload = {}
            canUpload = true
            onUpload = {}
            onClose = null
            configure()
        }
    )
