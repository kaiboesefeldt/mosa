package straightway.mosa.client.ui.identityconnected.shell

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*
import straightway.mosa.client.ui.identityconnected.moderation.*

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
