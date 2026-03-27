@file:OptIn(ExperimentalWasmJsInterop::class)

package straightway.mosa.client.ui.identityconnected

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.Key
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.ui.IdentityVisualsFactory
import straightway.mosa.content.QueryResult
import web.cssom.ClassName

internal fun ChildrenBuilder.repliesColumn(
    translator: Translator,
    props: IdentityConnectedViewRepliesColumnProps,
) = div {
    className = ClassName("topic-board__column topic-board__column--replies")
    ref = props.containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    div {
        className = ClassName("topic-board__column-header")
        if (props.isMobile && props.showBackToPosts) {
            button {
                className = ClassName("topic-board__back-button")
                ariaLabel = translator.translate("connected.mobile.backToPosts")
                onClick = { props.onBackToPosts() }
                i {
                    className = ClassName("fa fa-solid fa-chevron-left topic-board__back-icon")
                }
                span {
                    className = ClassName("topic-board__back-label")
                    +translator.translate("connected.mobile.backToPosts")
                }
            }
        }
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.replies.title")
        }
    }
    when {
        props.selectedPost == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.empty")
            }
        props.content.state.isLoadingReplies && props.content.state.replies == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.loading")
            }
        else -> {
            replyOriginCard(translator, props.visualsFactory, props.selectedPost)
            val replyEntries = props.content.state.replies?.entries.orEmpty()
            if (replyEntries.isEmpty() && !props.content.state.isLoadingReplies) {
                div {
                    className = ClassName("topic-board__empty")
                    +translator.translate("connected.replies.none")
                }
            }
            replyEntries.forEach { reply ->
                replyCard(
                    IdentityConnectedViewReplyCardProps(
                        entryContext =
                            IdentityConnectedViewEntryContext(
                                translator = translator,
                                visualsFactory = props.visualsFactory,
                                entry = reply,
                                connectedGid = props.connectedGid,
                            ),
                        entryActions = props.buildEntryActions(reply),
                    ),
                )
            }
        }
    }
    if (!props.content.state.isLoadingReplies &&
        props.content.state.replies != null &&
        props.content.state.replies.entries.isNotEmpty()
    ) {
        div {
            ref = props.loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
    div {
        className = ClassName("reply-composer-wrapper")
        div {
            className = ClassName("reply-composer-layer")
            if (props.content.state.isReplyComposerOpen) {
                replyComposerPopover(
                    translator = translator,
                    text = props.content.state.replyComposerText,
                    isPublishing = props.content.state.isReplyPublishing,
                    errorText = props.content.state.replyPublishError,
                    onTextChange = props.content.actions.setReplyComposerText,
                    onSend = props.content.actions.sendReply,
                    onClose = props.content.actions.closeReplyComposer,
                )
            }
            div {
                className = ClassName("reply-composer-fab")
                replyComposerButton(
                    label = translator.translate("connected.replies.compose.open"),
                    disabled = props.selectedPost == null || props.content.state.isReplyPublishing,
                    onActivate = props.content.actions.openReplyComposer,
                )
            }
        }
    }
}

private fun IdentityConnectedViewRepliesColumnProps.buildEntryActions(entry: QueryResult.Entry): IdentityConnectedViewEntryActions =
    IdentityConnectedViewEntryActions(
        canDeleteAsAdmin = canDeleteAsAdmin,
        deleteCandidateId = content.state.deleteCandidate?.id,
        isDeleting = content.state.isDeletingPost,
        deleteError = content.state.deleteError,
        onRequestDelete = { content.actions.requestDelete(entry) },
        onCancelDelete = content.actions.clearDeleteCandidate,
        onConfirmDelete = { content.actions.confirmDelete(entry) },
        canComplain = connectedGid != null && entry.authorGID != connectedGid,
        isComplaining = moderation.state.complaintActionPostId == entry.id,
        complaintComposerPostId = moderation.state.complaintComposerPostId,
        complaintComposerText = moderation.state.complaintComposerText,
        complaintComposerError = moderation.state.complaintComposerError,
        isComplaintSubmitting = moderation.state.isComplaintSubmitting,
        onRequestComplaint = { moderation.actions.requestPostComplaint(entry) },
        onComplaintTextChange = moderation.actions.setComplaintComposerText,
        onCloseComplaintComposer = moderation.actions.closeComplaintComposer,
        onSubmitComplaint = moderation.actions.submitPostComplaint,
        adminContact =
            IdentityConnectedViewAdminContactActions(
                canComposeAdminMessage = canDeleteAsAdmin,
                onToggleAdminUserCard = admin.actions.toggleAdminUserCard,
            ),
    )

private fun ChildrenBuilder.replyCard(
    props: IdentityConnectedViewReplyCardProps,
) {
    val entryContext = props.entryContext
    val entryActions = props.entryActions
    val authorVisuals = entryContext.visualsFactory.createFromGid(entryContext.entry.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val canOpenAdminUserCard =
        entryActions.adminContact.canComposeAdminMessage &&
            entryContext.connectedGid != null &&
            entryContext.entry.authorGID != entryContext.connectedGid
    val canDeleteReply =
        (entryContext.connectedGid != null && entryContext.entry.authorGID == entryContext.connectedGid) ||
            entryActions.canDeleteAsAdmin
    val showDeletePopover = canDeleteReply && entryActions.deleteCandidateId == entryContext.entry.id
    val showComplaintComposer = entryActions.complaintComposerPostId == entryContext.entry.id
    div {
        key = entryContext.entry.id.unsafeCast<Key>()
        className = ClassName("reply-card")
        div {
            className = ClassName("reply-card__author-row")
            renderEntryAuthor(
                translator = entryContext.translator,
                entryId = entryContext.entry.id,
                entryAuthorGid = entryContext.entry.authorGID,
                authorName = authorName,
                avatar = authorVisuals.avatar,
                timestamp = null,
                avatarClassName = "reply-card__avatar",
                detailsClassName = "reply-card__details",
                authorNameClassName = "reply-card__author-name",
                timestampClassName = "reply-card__timestamp",
                canOpenAdminUserCard = canOpenAdminUserCard,
                onToggleAdminUserCard = entryActions.adminContact.onToggleAdminUserCard,
            )
            div {
                className = ClassName("reply-card__meta")
                span {
                    className = ClassName("reply-card__timestamp")
                    +formatTimestamp(entryContext.entry.storedAt)
                }
                if (canDeleteReply) {
                    val deleteLabel = entryContext.translator.translate("connected.posts.delete.action")
                    button {
                        className = ClassName("post-card__delete reply-card__delete")
                        ariaLabel = deleteLabel
                        title = deleteLabel
                        onClick = { entryActions.onRequestDelete() }
                        i {
                            className = ClassName("fa fa-solid fa-trash")
                        }
                    }
                }
                if (entryActions.canComplain) {
                    val complaintLabel = entryContext.translator.translate("connected.complaints.post.action")
                    button {
                        className = ClassName("post-card__filter")
                        disabled = entryActions.isComplaining
                        ariaLabel = complaintLabel
                        title = complaintLabel
                        onClick = { entryActions.onRequestComplaint() }
                        i {
                            className = ClassName("fa fa-regular fa-flag")
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("reply-card__content")
            +entryContext.entry.content
        }
        if (showComplaintComposer) {
            div {
                className = ClassName("post-card__delete-popover complaint-composer complaint-composer--card")
                complaintComposerPopover(
                    ComplaintComposerPopoverProps(
                        translator = entryContext.translator,
                        title = entryContext.translator.translate("connected.complaints.post.title"),
                        placeholder = entryContext.translator.translate("connected.complaints.post.placeholder"),
                        text = entryActions.complaintComposerText,
                        isSubmitting = entryActions.isComplaintSubmitting,
                        errorText = entryActions.complaintComposerError,
                        sendLabel = entryContext.translator.translate("connected.complaints.post.send"),
                        onTextChange = entryActions.onComplaintTextChange,
                        onSend = entryActions.onSubmitComplaint,
                        onClose = entryActions.onCloseComplaintComposer,
                    ),
                )
            }
        }
        if (showDeletePopover) {
            val cancelLabel = entryContext.translator.translate("connected.posts.delete.cancel")
            val confirmLabel = entryContext.translator.translate("connected.posts.delete.confirm")
            div {
                className = ClassName("post-card__delete-popover")
                span {
                    className = ClassName("post-card__delete-title")
                    +entryContext.translator.translate("connected.posts.delete.title")
                }
                div {
                    className = ClassName("post-card__delete-content")
                    +entryContext.entry.content
                }
                if (entryActions.deleteError != null) {
                    div {
                        className = ClassName("post-card__delete-error")
                        +entryActions.deleteError
                    }
                }
                div {
                    className = ClassName("post-card__delete-actions")
                    button {
                        className = ClassName("button button--secondary post-card__delete-cancel")
                        disabled = entryActions.isDeleting
                        onClick = { entryActions.onCancelDelete() }
                        +cancelLabel
                    }
                    button {
                        className = ClassName("button button--danger post-card__delete-confirm")
                        disabled = entryActions.isDeleting
                        onClick = { entryActions.onConfirmDelete() }
                        +confirmLabel
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.replyOriginCard(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    origin: QueryResult.Entry,
) {
    val authorVisuals = visualsFactory.createFromGid(origin.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(origin.storedAt)
    div {
        key = origin.id.unsafeCast<Key>()
        className = ClassName("reply-origin")
        span {
            className = ClassName("reply-origin__badge")
            +translator.translate("connected.replies.source")
        }
        div {
            className = ClassName("reply-origin__author-row")
            img {
                className = ClassName("reply-origin__avatar")
                src = authorVisuals.avatar
                alt = authorName
            }
            div {
                className = ClassName("reply-origin__author-details")
                span {
                    className = ClassName("reply-origin__author-name")
                    +authorName
                }
                span {
                    className = ClassName("reply-origin__timestamp")
                    +timestamp
                }
            }
        }
        div {
            className = ClassName("reply-origin__content")
            +origin.content
        }
    }
}
