package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.Key
import react.Ref
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.message.AdminMessage
import web.cssom.ClassName
import web.html.HTMLDivElement

internal data class ComplaintQueueEntry(
    val complaint: OpenPostComplaint,
    val state: PostComplaintState,
)

internal fun OpenPostComplaint.toQueueEntry(): ComplaintQueueEntry = ComplaintQueueEntry(complaint = this, state = state)

internal val ComplaintQueueEntry.complaintId: String
    get() = complaint.complaintId

internal fun List<ComplaintQueueEntry>.filteredForComplaintQueue(showProcessedEntries: Boolean): List<ComplaintQueueEntry> {
    if (showProcessedEntries) {
        return this
    }
    return filter(ComplaintQueueEntry::isOpen)
}

internal fun ChildrenBuilder.adminMessageInboxPopover(
    props: AdminMessageInboxPopoverProps,
) = div {
    ref = props.popoverRef
    className = ClassName("message-inbox")
    onScroll = { event ->
        val target = event.currentTarget
        if (props.hasMore && isScrolledNearBottom(target)) {
            props.onScrollToEnd()
        }
    }
    div {
        className = ClassName("message-inbox__header")
        span {
            className = ClassName("message-inbox__title")
            +props.translator.translate("connected.messages.inbox.title")
        }
        button {
            className =
                if (props.showSeenMessages) {
                    ClassName("message-inbox__filter message-inbox__filter--active")
                } else {
                    ClassName("message-inbox__filter")
                }
            ariaLabel =
                props.translator.translate(
                    if (props.showSeenMessages) {
                        "connected.messages.inbox.hideRead"
                    } else {
                        "connected.messages.inbox.showRead"
                    },
                )
            onClick = { props.onToggleSeenVisibility() }
            +props.translator.translate(
                if (props.showSeenMessages) {
                    "connected.messages.inbox.hideRead"
                } else {
                    "connected.messages.inbox.showRead"
                },
            )
        }
        button {
            className = ClassName("message-inbox__close")
            ariaLabel = props.translator.translate("connected.messages.inbox.close")
            onClick = { props.onClose() }
            +"×"
        }
    }
    when {
        props.entries.isEmpty() && props.isLoading ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate("connected.messages.inbox.loading")
            }
        props.entries.isEmpty() && props.errorText != null ->
            div {
                className = ClassName("message-inbox__error")
                +props.errorText
            }
        props.entries.isEmpty() ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate(
                    if (!props.showSeenMessages && props.hasHiddenSeenEntries) {
                        "connected.messages.inbox.emptyUnread"
                    } else {
                        "connected.messages.inbox.empty"
                    },
                )
        }
        else -> {
            val readIds = props.readMessageIds.toSet()
            props.entries.forEach { entry ->
                val senderVisuals = props.identityVisualsFactory.createFromGid(entry.senderGid)
                val senderName = senderVisuals.name.toString().trim()
                div {
                    key = entry.id.unsafeCast<Key>()
                    className =
                        ClassName(
                            if (entry.id in readIds) {
                                "message-inbox__entry message-inbox__entry--read"
                            } else {
                                "message-inbox__entry"
                            },
                        )
                    div {
                        className = ClassName("message-inbox__sender")
                        img {
                            className = ClassName("message-inbox__sender-avatar")
                            src = senderVisuals.avatar
                            alt = senderName
                        }
                        div {
                            className = ClassName("message-inbox__sender-details")
                                span {
                                    className = ClassName("message-inbox__sender-name")
                                    +props.translator.translate(
                                        "connected.messages.inbox.from",
                                        mapOf("name" to senderName),
                                    )
                            }
                            span {
                                className = ClassName("message-inbox__timestamp")
                                +formatTimestamp(entry.createdAt)
                            }
                        }
                    }
                    span {
                        className = ClassName("message-inbox__subject")
                        +entry.subject
                    }
                    if (props.showSeenMessages && props.hasReadEntries && entry.id in readIds) {
                        span {
                            className = ClassName("message-inbox__status")
                            +props.translator.translate("connected.messages.inbox.read")
                        }
                    }
                    div {
                        className = ClassName("message-inbox__body")
                        +entry.body
                    }
                }
            }
            if (props.errorText != null) {
                div {
                    className = ClassName("message-inbox__error")
                    +props.errorText
                }
            } else if (props.isLoading || props.isAppending) {
                div {
                    className = ClassName("message-inbox__meta")
                    +props.translator.translate(
                        if (props.isAppending) {
                            "connected.messages.inbox.loadingMore"
                        } else {
                            "connected.messages.inbox.loading"
                        },
                    )
                }
            }
        }
    }
}

internal fun ChildrenBuilder.postComplaintQueuePopover(
    props: PostComplaintQueuePopoverProps,
) = div {
    ref = props.popoverRef
    className = ClassName("message-inbox complaint-queue")
    div {
        className = ClassName("message-inbox__header")
        span {
            className = ClassName("message-inbox__title")
            +props.translator.translate("connected.complaints.admin.title")
        }
        button {
            className =
                if (props.showProcessedEntries) {
                    ClassName("message-inbox__filter message-inbox__filter--active")
                } else {
                    ClassName("message-inbox__filter")
                }
            ariaLabel =
                props.translator.translate(
                    if (props.showProcessedEntries) {
                        "connected.complaints.admin.hideProcessed"
                    } else {
                        "connected.complaints.admin.showProcessed"
                    },
                )
            onClick = { props.onToggleProcessedVisibility() }
            +props.translator.translate(
                if (props.showProcessedEntries) {
                    "connected.complaints.admin.hideProcessed"
                } else {
                    "connected.complaints.admin.showProcessed"
                },
            )
        }
        button {
            className = ClassName("message-inbox__close")
            ariaLabel = props.translator.translate("connected.complaints.admin.close")
            onClick = { props.onClose() }
            +"×"
        }
    }
    when {
        props.isLoading ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate("connected.complaints.admin.loading")
            }
        props.errorText != null ->
            div {
                className = ClassName("message-inbox__error")
                +props.errorText
            }
        props.entries.isEmpty() ->
            div {
                className = ClassName("message-inbox__empty")
                +props.translator.translate(
                    if (!props.showProcessedEntries && props.hasHiddenProcessedEntries) {
                        "connected.complaints.admin.emptyOpen"
                    } else {
                        "connected.complaints.admin.empty"
                    },
                )
        }
        else -> {
            props.entries.forEach { entry ->
                val complaint = entry.complaint
                val complainantVisuals = props.identityVisualsFactory.createFromGid(complaint.complainantGid)
                val postAuthorVisuals = props.identityVisualsFactory.createFromGid(complaint.postAuthorGid)
                val showDecisionComposer = props.decisionEntryId == complaint.complaintId
                div {
                    key = complaint.complaintId.unsafeCast<Key>()
                    className = ClassName("message-inbox__entry complaint-queue__entry")
                    div {
                        className = ClassName("message-inbox__sender")
                        img {
                            className = ClassName("message-inbox__sender-avatar")
                            src = complainantVisuals.avatar
                            alt = complainantVisuals.name.toString()
                        }
                        div {
                            className = ClassName("message-inbox__sender-details")
                                span {
                                    className = ClassName("message-inbox__sender-name")
                                    +props.translator.translate(
                                        "connected.complaints.admin.from",
                                        mapOf("name" to complainantVisuals.name.toString().trim()),
                                    )
                            }
                            span {
                                className = ClassName("message-inbox__timestamp")
                                +formatTimestamp(complaint.createdAt)
                            }
                        }
                    }
                    span {
                        className = ClassName("message-inbox__subject")
                        +props.translator.translate(
                            "connected.complaints.admin.postAuthor",
                            mapOf("name" to postAuthorVisuals.name.toString().trim()),
                        )
                    }
                    div {
                        className = ClassName("message-inbox__body")
                        +complaint.postText
                    }
                    div {
                        className = ClassName("message-inbox__meta")
                        +props.translator.translate(
                            "connected.complaints.admin.reason",
                            mapOf("reason" to complaint.reason),
                        )
                    }
                    complaintQueueStateLabel(props.translator, entry.state)?.let { stateLabel ->
                        div {
                            className = ClassName("message-inbox__meta")
                            +stateLabel
                        }
                    }
                    if (entry.availableActions().isNotEmpty()) {
                        div {
                            className = ClassName("complaint-queue__actions")
                            entry.availableActions().forEach { targetState ->
                                button {
                                    className =
                                        ClassName(
                                            when (targetState) {
                                                PostComplaintState.DELETED -> "button button--primary complaint-queue__action"
                                                PostComplaintState.CONFIRMED, PostComplaintState.REJECTED ->
                                                    "button button--secondary complaint-queue__action"
                                                PostComplaintState.OPEN -> "button button--secondary complaint-queue__action"
                                            },
                                        )
                                    disabled = props.isDecisionSubmitting
                                    onClick = {
                                        when (targetState) {
                                            PostComplaintState.CONFIRMED -> props.onConfirm(entry)
                                            PostComplaintState.REJECTED -> props.onReject(entry)
                                            PostComplaintState.DELETED -> props.onDelete(entry)
                                            PostComplaintState.OPEN -> Unit
                                        }
                                    }
                                    +props.translator.translate(complaintQueueActionLabelKey(targetState))
                                }
                            }
                        }
                    }
                    if (showDecisionComposer) {
                        complaintComposerPopover(
                            ComplaintComposerPopoverProps(
                                translator = props.translator,
                                title = complaintDecisionTitle(props.translator, props.decisionTargetStatus),
                                placeholder = props.translator.translate("connected.complaints.admin.feedbackPlaceholder"),
                                text = props.decisionText,
                                isSubmitting = props.isDecisionSubmitting,
                                errorText = props.decisionErrorText,
                                sendLabel = props.translator.translate("connected.complaints.admin.send"),
                                onTextChange = props.onDecisionTextChange,
                                onSend = props.onSubmitDecision,
                                onClose = props.onCloseDecisionComposer,
                            ),
                        )
                    }
                }
            }
            if (props.hasMore) {
                button {
                    className = ClassName("button button--secondary complaint-queue__load-more")
                    disabled = props.isAppending
                    onClick = { props.onLoadMore() }
                    +props.translator.translate("connected.complaints.admin.loadMore")
                }
            }
        }
    }
}

private fun complaintDecisionTitle(
    translator: Translator,
    targetStatus: PostComplaintState?,
): String =
    when (targetStatus) {
        PostComplaintState.CONFIRMED -> translator.translate("connected.complaints.admin.composeConfirmed")
        PostComplaintState.REJECTED -> translator.translate("connected.complaints.admin.composeRejected")
        PostComplaintState.DELETED -> translator.translate("connected.complaints.admin.composeDeleted")
        PostComplaintState.OPEN, null -> translator.translate("connected.complaints.admin.title")
    }

internal fun ComplaintQueueEntry.isOpen(): Boolean = state == PostComplaintState.OPEN

internal fun ComplaintQueueEntry.canTransitionTo(targetState: PostComplaintState): Boolean =
    when (state) {
        PostComplaintState.OPEN -> targetState != PostComplaintState.OPEN
        PostComplaintState.REJECTED -> targetState == PostComplaintState.CONFIRMED
        PostComplaintState.CONFIRMED -> targetState == PostComplaintState.REJECTED
        PostComplaintState.DELETED -> false
    }

private fun ComplaintQueueEntry.availableActions(): List<PostComplaintState> =
    when (state) {
        PostComplaintState.OPEN ->
            listOf(
                PostComplaintState.CONFIRMED,
                PostComplaintState.REJECTED,
                PostComplaintState.DELETED,
            )
        PostComplaintState.REJECTED -> listOf(PostComplaintState.CONFIRMED)
        PostComplaintState.CONFIRMED -> listOf(PostComplaintState.REJECTED)
        PostComplaintState.DELETED -> emptyList()
    }

private fun complaintQueueActionLabelKey(targetState: PostComplaintState): String =
    when (targetState) {
        PostComplaintState.CONFIRMED -> "connected.complaints.admin.confirm"
        PostComplaintState.REJECTED -> "connected.complaints.admin.reject"
        PostComplaintState.DELETED -> "connected.complaints.admin.delete"
        PostComplaintState.OPEN -> "connected.complaints.admin.title"
    }

private fun complaintQueueStateLabel(
    translator: Translator,
    state: PostComplaintState,
): String? =
    when (state) {
        PostComplaintState.OPEN -> null
        PostComplaintState.CONFIRMED -> translator.translate("connected.complaints.admin.stateConfirmed")
        PostComplaintState.REJECTED -> translator.translate("connected.complaints.admin.stateRejected")
        PostComplaintState.DELETED -> translator.translate("connected.complaints.admin.stateDeleted")
    }
