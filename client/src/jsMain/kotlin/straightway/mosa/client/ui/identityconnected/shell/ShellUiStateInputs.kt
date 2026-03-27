package straightway.mosa.client.ui.identityconnected.shell

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*
import straightway.mosa.client.ui.identityconnected.moderation.*

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
