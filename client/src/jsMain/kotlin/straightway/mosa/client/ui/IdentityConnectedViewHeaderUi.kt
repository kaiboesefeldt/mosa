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
    props: IdentityConnectedViewHeaderProps,
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
            ref = props.moderation.inboxButtonRef
            className = ClassName("connected-shell__inbox-button")
            ariaLabel = translator.translate("connected.messages.inbox.button")
            title = translator.translate("connected.messages.inbox.button")
            onClick = {
                if (props.moderation.isInboxOpen) props.moderation.closeInbox() else props.moderation.loadInbox()
            }
            i { className = ClassName("fa fa-solid fa-bullhorn") }
            if (0 < props.moderation.unreadInboxCount) {
                span {
                    className = ClassName("connected-shell__inbox-badge")
                    +props.moderation.unreadInboxCount.toString()
                }
            }
        }
        if (props.adminPresentation.showsConnectedAdminBadge) {
            button {
                ref = props.moderation.complaintQueueButtonRef
                className = ClassName("connected-shell__inbox-button")
                ariaLabel = translator.translate("connected.complaints.admin.button")
                title = translator.translate("connected.complaints.admin.button")
                onClick = {
                    if (props.moderation.isComplaintQueueOpen) {
                        props.moderation.closeComplaintQueue()
                    } else {
                        props.moderation.loadComplaintQueue()
                    }
                }
                i { className = ClassName("fa fa-solid fa-shield-halved") }
                if (0 < props.moderation.openComplaintCount) {
                    span {
                        className = ClassName("connected-shell__inbox-badge")
                        +props.moderation.openComplaintCount.toString()
                    }
                }
            }
        }
        span {
            className = ClassName("connected-shell__identity-name")
            +props.identityName
        }
        button {
            className =
                ClassName(
                    if (props.adminPresentation.showsConnectedAdminBadge) {
                        "connected-shell__avatar-button connected-shell__avatar-button--admin"
                    } else {
                        "connected-shell__avatar-button"
                    },
                )
            ariaLabel = translator.translate("connected.profile.avatar.aria")
            title = props.uiState.avatarDescription
            onClick = { props.profileController.showProfile() }
            val avatar = props.profile.identityAvatar
            if (avatar != null) {
                img {
                    className = ClassName("connected-shell__avatar-image")
                    src = avatar
                    alt = props.uiState.avatarDescription
                }
            } else {
                span {
                    className = ClassName("connected-shell__avatar-fallback")
                    +(props.identityName.firstOrNull()?.uppercaseChar()?.toString() ?: "?")
                }
            }
            if (props.adminPresentation.showsConnectedAdminBadge) {
                span {
                    className = ClassName("connected-shell__avatar-badge")
                    +translator.translate("identity.admin.badge")
                }
            }
        }
        if (props.moderation.isInboxOpen) {
            adminMessageInboxPopover(
                translator = translator,
                identityVisualsFactory = props.visualsFactory,
                entries = props.moderation.visibleInboxEntries,
                isLoading = props.moderation.isInboxLoading,
                isAppending = props.moderation.isInboxAppending,
                errorText = props.moderation.inboxError,
                hasMore = props.moderation.inboxHasMore,
                showSeenMessages = props.moderation.showSeenInboxEntries,
                hasHiddenSeenEntries = props.moderation.hasHiddenSeenInboxEntries,
                readMessageIds = props.moderation.seenInboxMessageIds,
                hasReadEntries = props.moderation.hasReadInboxEntries,
                popoverRef = props.moderation.inboxPopoverRef,
                onToggleSeenVisibility = props.moderation.toggleSeenInboxVisibility,
                onClose = props.moderation.closeInbox,
                onScrollToEnd = props.moderation.loadMoreInbox,
            )
        }
        if (props.moderation.isComplaintQueueOpen) {
            postComplaintQueuePopover(
                translator = translator,
                identityVisualsFactory = props.visualsFactory,
                entries = props.moderation.visibleComplaintQueueEntries,
                isLoading = props.moderation.isComplaintQueueLoading,
                isAppending = props.moderation.isComplaintQueueAppending,
                errorText = props.moderation.complaintQueueError,
                hasMore = props.moderation.complaintQueueHasMore,
                showProcessedEntries = props.moderation.showProcessedComplaintEntries,
                hasHiddenProcessedEntries = props.moderation.hasHiddenProcessedComplaintEntries,
                popoverRef = props.moderation.complaintQueuePopoverRef,
                onToggleProcessedVisibility = props.moderation.toggleProcessedComplaintVisibility,
                onClose = props.moderation.closeComplaintQueue,
                onLoadMore = props.moderation.loadMoreComplaintQueue,
                decisionEntryId = props.moderation.complaintDecisionEntryId,
                decisionTargetStatus = props.moderation.complaintDecisionTargetStatus,
                decisionText = props.moderation.complaintDecisionText,
                decisionErrorText = props.moderation.complaintDecisionError,
                isDecisionSubmitting = props.moderation.isComplaintDecisionSubmitting,
                onDecisionTextChange = props.moderation.setComplaintDecisionText,
                onCloseDecisionComposer = props.moderation.closeComplaintDecisionComposer,
                onSubmitDecision = props.moderation.submitComplaintDecision,
                onConfirm = { props.moderation.startComplaintDecision(it, PostComplaintState.CONFIRMED) },
                onReject = { props.moderation.startComplaintDecision(it, PostComplaintState.REJECTED) },
                onDelete = { props.moderation.startComplaintDecision(it, PostComplaintState.DELETED) },
            )
        }
    }
}
