package straightway.mosa.client.ui

import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.render

internal fun renderIdentityLandingView(
    configure: IdentityLandingViewProps.() -> Unit = {},
): RenderResult =
    render(
        IdentityLandingView.create {
            AppModule.start()
            identityName = "Default Identity"
            isBusy = false
            errorText = null
            canConnect = true
            onConnect = {}
            onGenerateNew = {}
            configure()
        }
    )
