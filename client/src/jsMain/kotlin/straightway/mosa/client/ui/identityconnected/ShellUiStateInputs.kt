package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.ui.*

internal data class ShellUiStateInputs(
    val translator: Translator,
    val avatarTitleKey: String,
    val isMobile: Boolean,
    val mobilePane: MobilePane,
    val isProfileVisible: Boolean,
    val profileExitAnimation: ProfileExitAnimation,
)
