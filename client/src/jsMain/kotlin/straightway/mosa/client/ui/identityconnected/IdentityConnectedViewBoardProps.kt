package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.RefObject
import web.html.HTMLDivElement

internal data class IdentityConnectedViewBoardProps(
    val profile: IdentityScreen.Profile,
    val visualsFactory: IdentityVisualsFactory,
    val adminPresentation: IdentityAdminPresentation,
    val viewport: IdentityConnectedViewViewportController,
    val shell: IdentityConnectedViewMobileShell,
    val content: IdentityConnectedViewContentController,
    val moderation: IdentityConnectedViewModerationController,
    val admin: IdentityConnectedViewAdminController,
    val uiState: IdentityConnectedViewUiState,
    val postsContainerRef: RefObject<HTMLDivElement>,
    val loadMoreRef: RefObject<HTMLDivElement>,
    val repliesContainerRef: RefObject<HTMLDivElement>,
    val repliesLoadMoreRef: RefObject<HTMLDivElement>,
)
