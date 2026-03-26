package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import straightway.mosa.client.i18n.Translator

internal data class IdentityConnectedViewUiState(
    val avatarDescription: String,
    val shouldRenderFloatingComposer: Boolean,
    val shouldRenderProfile: Boolean,
)

internal fun buildIdentityConnectedViewUiState(
    inputs: IdentityConnectedViewUiStateInputs,
): IdentityConnectedViewUiState =
    IdentityConnectedViewUiState(
        avatarDescription = inputs.translator.translate(inputs.avatarTitleKey),
        shouldRenderFloatingComposer = !inputs.isMobile || inputs.mobilePane != MobilePane.Replies,
        shouldRenderProfile = inputs.isProfileVisible || inputs.profileExitAnimation != ProfileExitAnimation.None,
    )
