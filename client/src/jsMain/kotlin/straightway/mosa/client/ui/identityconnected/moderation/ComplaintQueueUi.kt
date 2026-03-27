package straightway.mosa.client.ui.identityconnected.moderation

import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*

import kotlin.js.unsafeCast
import react.ChildrenBuilder
import react.Key
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import straightway.mosa.content.complaint.PostComplaintState
import web.cssom.ClassName

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
                renderComplaintQueueEntry(props, entry)
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

private fun ChildrenBuilder.renderComplaintQueueEntry(
    props: PostComplaintQueuePopoverProps,
    entry: ComplaintQueueEntry,
) {
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
