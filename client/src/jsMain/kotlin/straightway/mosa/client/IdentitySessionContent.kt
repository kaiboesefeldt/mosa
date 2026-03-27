package straightway.mosa.client

import react.FC
import react.Props
import straightway.mosa.client.ui.IdentityProfileView
import straightway.mosa.client.ui.IdentityScreen
import straightway.mosa.client.ui.applyFrom
import straightway.mosa.client.ui.identityconnected.IdentityConnectedView
import straightway.mosa.client.ui.useIdentitySession

internal val IdentitySessionContent = FC<Props> {
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
