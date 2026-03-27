package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.RefObject
import web.html.HTMLDivElement

internal data class BoardProps(
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val viewport: ViewportController,
    val shell: MobileShell,
    val content: ContentController,
    val moderation: ModerationController,
    val admin: AdminController,
    val uiState: ShellUiState,
    val postsContainerRef: RefObject<HTMLDivElement>,
    val loadMoreRef: RefObject<HTMLDivElement>,
    val repliesContainerRef: RefObject<HTMLDivElement>,
    val repliesLoadMoreRef: RefObject<HTMLDivElement>,
)
