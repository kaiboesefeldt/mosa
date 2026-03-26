package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

internal data class IdentityConnectedViewOverlayProps(
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val profileController: IdentityConnectedViewProfileController,
    val content: IdentityConnectedViewContentController,
    val admin: IdentityConnectedViewAdminController,
    val uiState: IdentityConnectedViewUiState,
)
