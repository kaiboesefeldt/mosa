package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

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
    props: HeaderProps,
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
            ref = props.moderation.refs.inboxButtonRef
            className = ClassName("connected-shell__inbox-button")
            ariaLabel = translator.translate("connected.messages.inbox.button")
            title = translator.translate("connected.messages.inbox.button")
            onClick = {
                if (props.moderation.state.isInboxOpen) props.moderation.actions.closeInbox() else props.moderation.actions.loadInbox()
            }
            i { className = ClassName("fa fa-solid fa-bullhorn") }
            if (0 < props.moderation.state.unreadInboxCount) {
                span {
                    className = ClassName("connected-shell__inbox-badge")
                    +props.moderation.state.unreadInboxCount.toString()
                }
            }
        }
        if (props.adminPresentation.showsConnectedAdminBadge) {
            button {
                ref = props.moderation.refs.complaintQueueButtonRef
                className = ClassName("connected-shell__inbox-button")
                ariaLabel = translator.translate("connected.complaints.admin.button")
                title = translator.translate("connected.complaints.admin.button")
                onClick = {
                    if (props.moderation.state.isComplaintQueueOpen) {
                        props.moderation.actions.closeComplaintQueue()
                    } else {
                        props.moderation.actions.loadComplaintQueue()
                    }
                }
                i { className = ClassName("fa fa-solid fa-shield-halved") }
                if (0 < props.moderation.state.openComplaintCount) {
                    span {
                        className = ClassName("connected-shell__inbox-badge")
                        +props.moderation.state.openComplaintCount.toString()
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
            onClick = { props.profileController.actions.showProfile() }
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
        if (props.moderation.state.isInboxOpen) {
            adminMessageInboxPopover(
                AdminMessageInboxPopoverProps(
                    translator = translator,
                    identityVisualsFactory = props.visualsFactory,
                    entries = props.moderation.state.visibleInboxEntries,
                    isLoading = props.moderation.state.isInboxLoading,
                    isAppending = props.moderation.state.isInboxAppending,
                    errorText = props.moderation.state.inboxError,
                    hasMore = props.moderation.state.inboxHasMore,
                    showSeenMessages = props.moderation.state.showSeenInboxEntries,
                    hasHiddenSeenEntries = props.moderation.state.hasHiddenSeenInboxEntries,
                    readMessageIds = props.moderation.state.seenInboxMessageIds,
                    hasReadEntries = props.moderation.state.hasReadInboxEntries,
                    popoverRef = props.moderation.refs.inboxPopoverRef,
                    onToggleSeenVisibility = props.moderation.actions.toggleSeenInboxVisibility,
                    onClose = props.moderation.actions.closeInbox,
                    onScrollToEnd = props.moderation.actions.loadMoreInbox,
                ),
            )
        }
        if (props.moderation.state.isComplaintQueueOpen) {
            postComplaintQueuePopover(
                PostComplaintQueuePopoverProps(
                    translator = translator,
                    identityVisualsFactory = props.visualsFactory,
                    entries = props.moderation.state.visibleComplaintQueueEntries,
                    isLoading = props.moderation.state.isComplaintQueueLoading,
                    isAppending = props.moderation.state.isComplaintQueueAppending,
                    errorText = props.moderation.state.complaintQueueError,
                    hasMore = props.moderation.state.complaintQueueHasMore,
                    showProcessedEntries = props.moderation.state.showProcessedComplaintEntries,
                    hasHiddenProcessedEntries = props.moderation.state.hasHiddenProcessedComplaintEntries,
                    popoverRef = props.moderation.refs.complaintQueuePopoverRef,
                    onToggleProcessedVisibility = props.moderation.actions.toggleProcessedComplaintVisibility,
                    onClose = props.moderation.actions.closeComplaintQueue,
                    onLoadMore = props.moderation.actions.loadMoreComplaintQueue,
                    decisionEntryId = props.moderation.state.complaintDecisionEntryId,
                    decisionTargetStatus = props.moderation.state.complaintDecisionTargetStatus,
                    decisionText = props.moderation.state.complaintDecisionText,
                    decisionErrorText = props.moderation.state.complaintDecisionError,
                    isDecisionSubmitting = props.moderation.state.isComplaintDecisionSubmitting,
                    onDecisionTextChange = props.moderation.actions.setComplaintDecisionText,
                    onCloseDecisionComposer = props.moderation.actions.closeComplaintDecisionComposer,
                    onSubmitDecision = props.moderation.actions.submitComplaintDecision,
                    onConfirm = { props.moderation.actions.startComplaintDecision(it, PostComplaintState.CONFIRMED) },
                    onReject = { props.moderation.actions.startComplaintDecision(it, PostComplaintState.REJECTED) },
                    onDelete = { props.moderation.actions.startComplaintDecision(it, PostComplaintState.DELETED) },
                ),
            )
        }
    }
}
