package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import straightway.mosa.client.assets.LogoResources
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.complaint.PostComplaintState
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedShellHeader(
    translator: Translator,
    identityName: String,
    profile: IdentityScreen.Profile,
    visualsFactory: IdentityVisualsFactory,
    adminPresentation: IdentityAdminPresentation,
    profileController: IdentityConnectedViewProfileController,
    moderation: IdentityConnectedViewModerationController,
    uiState: IdentityConnectedViewUiState,
) = div {
    className = ClassName("connected-shell__header")
    div {
        className = ClassName("connected-shell__brand")
        img {
            className = ClassName("connected-shell__brand-logo")
            src = LogoResources.dataUrl
            alt = translator.translate("connected.brand.alt")
        }
        span {
            className = ClassName("connected-shell__brand-name")
            +translator.translate("connected.brand.name")
        }
    }
    div {
        className = ClassName("connected-shell__profile")
        button {
            ref = moderation.inboxButtonRef
            className = ClassName("connected-shell__inbox-button")
            ariaLabel = translator.translate("connected.messages.inbox.button")
            title = translator.translate("connected.messages.inbox.button")
            onClick = {
                if (moderation.isInboxOpen) moderation.closeInbox() else moderation.loadInbox()
            }
            i { className = ClassName("fa fa-solid fa-bullhorn") }
            if (0 < moderation.unreadInboxCount) {
                span {
                    className = ClassName("connected-shell__inbox-badge")
                    +moderation.unreadInboxCount.toString()
                }
            }
        }
        if (adminPresentation.showsConnectedAdminBadge) {
            button {
                ref = moderation.complaintQueueButtonRef
                className = ClassName("connected-shell__inbox-button")
                ariaLabel = translator.translate("connected.complaints.admin.button")
                title = translator.translate("connected.complaints.admin.button")
                onClick = {
                    if (moderation.isComplaintQueueOpen) moderation.closeComplaintQueue() else moderation.loadComplaintQueue()
                }
                i { className = ClassName("fa fa-solid fa-shield-halved") }
                if (0 < moderation.openComplaintCount) {
                    span {
                        className = ClassName("connected-shell__inbox-badge")
                        +moderation.openComplaintCount.toString()
                    }
                }
            }
        }
        span {
            className = ClassName("connected-shell__identity-name")
            +identityName
        }
        button {
            className =
                ClassName(
                    if (adminPresentation.showsConnectedAdminBadge) {
                        "connected-shell__avatar-button connected-shell__avatar-button--admin"
                    } else {
                        "connected-shell__avatar-button"
                    },
                )
            ariaLabel = translator.translate("connected.profile.avatar.aria")
            title = uiState.avatarDescription
            onClick = { profileController.showProfile() }
            val avatar = profile.identityAvatar
            if (avatar != null) {
                img {
                    className = ClassName("connected-shell__avatar-image")
                    src = avatar
                    alt = uiState.avatarDescription
                }
            } else {
                span {
                    className = ClassName("connected-shell__avatar-fallback")
                    +(identityName.firstOrNull()?.uppercaseChar()?.toString() ?: "?")
                }
            }
            if (adminPresentation.showsConnectedAdminBadge) {
                span {
                    className = ClassName("connected-shell__avatar-badge")
                    +translator.translate("identity.admin.badge")
                }
            }
        }
        if (moderation.isInboxOpen) {
            adminMessageInboxPopover(
                translator = translator,
                identityVisualsFactory = visualsFactory,
                entries = moderation.visibleInboxEntries,
                isLoading = moderation.isInboxLoading,
                isAppending = moderation.isInboxAppending,
                errorText = moderation.inboxError,
                hasMore = moderation.inboxHasMore,
                showSeenMessages = moderation.showSeenInboxEntries,
                hasHiddenSeenEntries = moderation.hasHiddenSeenInboxEntries,
                readMessageIds = moderation.seenInboxMessageIds,
                hasReadEntries = moderation.hasReadInboxEntries,
                popoverRef = moderation.inboxPopoverRef,
                onToggleSeenVisibility = moderation.toggleSeenInboxVisibility,
                onClose = moderation.closeInbox,
                onScrollToEnd = moderation.loadMoreInbox,
            )
        }
        if (moderation.isComplaintQueueOpen) {
            postComplaintQueuePopover(
                translator = translator,
                identityVisualsFactory = visualsFactory,
                entries = moderation.visibleComplaintQueueEntries,
                isLoading = moderation.isComplaintQueueLoading,
                isAppending = moderation.isComplaintQueueAppending,
                errorText = moderation.complaintQueueError,
                hasMore = moderation.complaintQueueHasMore,
                showProcessedEntries = moderation.showProcessedComplaintEntries,
                hasHiddenProcessedEntries = moderation.hasHiddenProcessedComplaintEntries,
                popoverRef = moderation.complaintQueuePopoverRef,
                onToggleProcessedVisibility = moderation.toggleProcessedComplaintVisibility,
                onClose = moderation.closeComplaintQueue,
                onLoadMore = moderation.loadMoreComplaintQueue,
                decisionEntryId = moderation.complaintDecisionEntryId,
                decisionTargetStatus = moderation.complaintDecisionTargetStatus,
                decisionText = moderation.complaintDecisionText,
                decisionErrorText = moderation.complaintDecisionError,
                isDecisionSubmitting = moderation.isComplaintDecisionSubmitting,
                onDecisionTextChange = moderation.setComplaintDecisionText,
                onCloseDecisionComposer = moderation.closeComplaintDecisionComposer,
                onSubmitDecision = moderation.submitComplaintDecision,
                onConfirm = { moderation.startComplaintDecision(it, PostComplaintState.CONFIRMED) },
                onReject = { moderation.startComplaintDecision(it, PostComplaintState.REJECTED) },
                onDelete = { moderation.startComplaintDecision(it, PostComplaintState.DELETED) },
            )
        }
    }
}
