package straightway.mosa.client.ui.identityconnected.shell

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.ui.*

internal data class OverlayProps(
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val profileController: ProfileController,
    val onProfileConnectRequested: (String?) -> Unit,
    val content: ContentController,
    val admin: AdminController,
    val uiState: ShellUiState,
)
