package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

internal data class OverlayProps(
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val profileController: ProfileController,
    val content: ContentController,
    val admin: AdminController,
    val uiState: ShellUiState,
)
