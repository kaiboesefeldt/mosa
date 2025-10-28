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
            pods = listOf("pod://default")
            selectedPod = "pod://default"
            isBusy = false
            errorText = null
            canConnect = true
            onSelectPod = {}
            onConnect = {}
            onGenerateNew = {}
            configure()
        }
    )
