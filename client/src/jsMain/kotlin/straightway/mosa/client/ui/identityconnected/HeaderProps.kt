package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

internal data class HeaderProps(
    val identityName: String,
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val profileController: ProfileController,
    val moderation: ModerationController,
    val uiState: ShellUiState,
)
