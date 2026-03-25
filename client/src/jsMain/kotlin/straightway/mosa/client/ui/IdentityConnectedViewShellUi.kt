package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.RefObject
import react.dom.html.ReactHTML.div
import web.cssom.ClassName
import web.html.HTMLDivElement

internal fun ChildrenBuilder.connectedShellLayout(
    translator: straightway.mosa.client.i18n.Translator,
    identityName: String,
    profile: IdentityScreen.Profile,
    visualsFactory: IdentityVisualsFactory,
    adminPresentation: IdentityAdminPresentation,
    profileController: IdentityConnectedViewProfileController,
    viewport: IdentityConnectedViewViewportController,
    shell: IdentityConnectedViewMobileShell,
    content: IdentityConnectedViewContentController,
    moderation: IdentityConnectedViewModerationController,
    admin: IdentityConnectedViewAdminController,
    uiState: IdentityConnectedViewUiState,
    postsContainerRef: RefObject<HTMLDivElement>,
    loadMoreRef: RefObject<HTMLDivElement>,
    repliesContainerRef: RefObject<HTMLDivElement>,
    repliesLoadMoreRef: RefObject<HTMLDivElement>,
) = div {
    className = ClassName("connected-shell")
    connectedShellHeader(
        translator = translator,
        identityName = identityName,
        profile = profile,
        visualsFactory = visualsFactory,
        adminPresentation = adminPresentation,
        profileController = profileController,
        moderation = moderation,
        uiState = uiState,
    )
    connectedContentBoard(
        translator = translator,
        profile = profile,
        visualsFactory = visualsFactory,
        adminPresentation = adminPresentation,
        viewport = viewport,
        shell = shell,
        content = content,
        moderation = moderation,
        admin = admin,
        uiState = uiState,
        postsContainerRef = postsContainerRef,
        loadMoreRef = loadMoreRef,
        repliesContainerRef = repliesContainerRef,
        repliesLoadMoreRef = repliesLoadMoreRef,
    )
}
