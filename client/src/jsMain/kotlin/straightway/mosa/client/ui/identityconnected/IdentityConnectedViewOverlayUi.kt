package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedShellOverlays(
    translator: straightway.mosa.client.i18n.Translator,
    props: IdentityConnectedViewOverlayProps,
) {
    if (props.uiState.shouldRenderProfile) {
        IdentityProfileView {
            applyFrom(props.profile.copy(isAdmin = props.adminPresentation.showsDisplayedAdminBadge))
            val connectProfile = props.profile.onConnect
            onClose = { props.profileController.hideProfile() }
            onConnect = {
                connectProfile()
                props.profileController.hideProfileWithSuction()
            }
            canDeletePosts = props.profile.canDeletePosts && !props.content.isDeletingAllPosts
            onDeletePosts = props.content.deleteAllPosts
            exitAnimation = props.profileController.profileExitAnimation
            entryAnimation = props.profileController.profileEntryAnimation
        }
    }

    if (props.content.isComposerOpen) {
        composerDialog(
            translator = translator,
            text = props.content.composerText,
            onTextChange = props.content.setComposerText,
            onClose = props.content.closeComposer,
            onSend = props.content.sendPost,
            isPublishing = props.content.isPublishing,
            errorText = props.content.publishError,
        )
    }

    props.profileController.toastMessage?.let { message ->
        div {
            className = ClassName("connected-toast")
            +message
        }
    }

    if (props.admin.adminUserCardAnchorId != null &&
        props.admin.adminMessageRecipientName != null &&
        props.admin.adminMessageRecipientAvatar != null
    ) {
        adminUserActionCardOverlay(
            translator = translator,
            identityVisualsFactory = props.visualsFactory,
            admin = props.admin,
        )
    }
}
