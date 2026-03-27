package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import straightway.mosa.client.i18n.Translator

internal data class ShellUiState(
    val avatarDescription: String,
    val shouldRenderFloatingComposer: Boolean,
    val shouldRenderProfile: Boolean,
)

internal fun buildShellUiState(
    inputs: ShellUiStateInputs,
): ShellUiState =
    ShellUiState(
        avatarDescription = inputs.translator.translate(inputs.avatarTitleKey),
        shouldRenderFloatingComposer = !inputs.isMobile || inputs.mobilePane != MobilePane.Replies,
        shouldRenderProfile = inputs.isProfileVisible || inputs.profileExitAnimation != ProfileExitAnimation.None,
    )
