package straightway.mosa.client.ui

import straightway.mosa.client.i18n.Translator

internal data class IdentityConnectedViewUiState(
    val avatarDescription: String,
    val shouldRenderFloatingComposer: Boolean,
    val shouldRenderProfile: Boolean,
)

internal fun buildIdentityConnectedViewUiState(
    translator: Translator,
    avatarTitleKey: String,
    isMobile: Boolean,
    mobilePane: MobilePane,
    isProfileVisible: Boolean,
    profileExitAnimation: ProfileExitAnimation,
): IdentityConnectedViewUiState =
    IdentityConnectedViewUiState(
        avatarDescription = translator.translate(avatarTitleKey),
        shouldRenderFloatingComposer = !isMobile || mobilePane != MobilePane.Replies,
        shouldRenderProfile = isProfileVisible || profileExitAnimation != ProfileExitAnimation.None,
    )
