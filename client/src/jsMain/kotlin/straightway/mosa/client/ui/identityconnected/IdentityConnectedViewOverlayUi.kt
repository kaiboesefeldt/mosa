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
            onClose = { props.profileController.actions.hideProfile() }
            onConnect = {
                connectProfile()
                props.profileController.actions.hideProfileWithSuction()
            }
            canDeletePosts = props.profile.canDeletePosts && !props.content.state.isDeletingAllPosts
            onDeletePosts = props.content.actions.deleteAllPosts
            exitAnimation = props.profileController.state.profileExitAnimation
            entryAnimation = props.profileController.state.profileEntryAnimation
        }
    }

    if (props.content.state.isComposerOpen) {
        composerDialog(
            translator = translator,
            text = props.content.state.composerText,
            onTextChange = props.content.actions.setComposerText,
            onClose = props.content.actions.closeComposer,
            onSend = props.content.actions.sendPost,
            isPublishing = props.content.state.isPublishing,
            errorText = props.content.state.publishError,
        )
    }

    props.profileController.state.toastMessage?.let { message ->
        div {
            className = ClassName("connected-toast")
            +message
        }
    }

    if (props.admin.state.adminUserCardAnchorId != null &&
        props.admin.state.adminMessageRecipientName != null &&
        props.admin.state.adminMessageRecipientAvatar != null
    ) {
        adminUserActionCardOverlay(
            translator = translator,
            identityVisualsFactory = props.visualsFactory,
            admin = props.admin,
        )
    }
}
