package straightway.mosa.client.ui

internal data class IdentityConnectedViewHeaderProps(
    val identityName: String,
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val profileController: IdentityConnectedViewProfileController,
    val moderation: IdentityConnectedViewModerationController,
    val uiState: IdentityConnectedViewUiState,
)
