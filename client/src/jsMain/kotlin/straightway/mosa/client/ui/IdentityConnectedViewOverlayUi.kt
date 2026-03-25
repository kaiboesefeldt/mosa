package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedShellOverlays(
    translator: straightway.mosa.client.i18n.Translator,
    profile: IdentityScreen.Profile,
    visualsFactory: IdentityVisualsFactory,
    adminPresentation: IdentityAdminPresentation,
    profileController: IdentityConnectedViewProfileController,
    content: IdentityConnectedViewContentController,
    admin: IdentityConnectedViewAdminController,
    uiState: IdentityConnectedViewUiState,
) {
    if (uiState.shouldRenderProfile) {
        IdentityProfileView {
            applyFrom(profile.copy(isAdmin = adminPresentation.showsDisplayedAdminBadge))
            val connectProfile = profile.onConnect
            onClose = { profileController.hideProfile() }
            onConnect = {
                connectProfile()
                profileController.hideProfileWithSuction()
            }
            canDeletePosts = profile.canDeletePosts && !content.isDeletingAllPosts
            onDeletePosts = content.deleteAllPosts
            exitAnimation = profileController.profileExitAnimation
            entryAnimation = profileController.profileEntryAnimation
        }
    }

    if (content.isComposerOpen) {
        composerDialog(
            translator = translator,
            text = content.composerText,
            onTextChange = content.setComposerText,
            onClose = content.closeComposer,
            onSend = content.sendPost,
            isPublishing = content.isPublishing,
            errorText = content.publishError,
        )
    }

    profileController.toastMessage?.let { message ->
        div {
            className = ClassName("connected-toast")
            +message
        }
    }

    if (admin.adminUserCardAnchorId != null &&
        admin.adminMessageRecipientName != null &&
        admin.adminMessageRecipientAvatar != null
    ) {
        adminUserActionCardOverlay(
            translator = translator,
            identityVisualsFactory = visualsFactory,
            recipientAvatar = admin.adminMessageRecipientAvatar,
            recipientName = admin.adminMessageRecipientName,
            isComposerOpen = admin.isAdminMessageComposerOpen,
            subject = admin.adminMessageSubject,
            body = admin.adminMessageBody,
            isSending = admin.isAdminMessageSending,
            errorText = admin.adminMessageError,
            historyEntries = admin.adminHistoryEntries,
            isHistoryLoading = admin.isAdminHistoryLoading,
            isHistoryAppending = admin.isAdminHistoryAppending,
            historyErrorText = admin.adminHistoryError,
            historyHasMore = admin.adminHistoryHasMore,
            historyListRef = admin.adminHistoryListRef,
            onOpenComposer = admin.openAdminMessageCompose,
            onSubjectChange = admin.setAdminMessageSubject,
            onBodyChange = admin.setAdminMessageBody,
            onClose = admin.closeAdminUserCard,
            onSend = admin.sendAdminMessageToCurrentRecipient,
            onHistoryScroll = admin.loadMoreAdminHistory,
        )
    }
}
