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
import straightway.mosa.client.ui.filterEntriesByScore
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.ui.IdentityVisualsFactory
import straightway.mosa.content.QueryResult
import web.cssom.ClassName

internal fun ChildrenBuilder.postsColumn(
    translator: Translator,
    props: IdentityConnectedViewPostsColumnProps,
) = div {
    className = ClassName("topic-board__column topic-board__column--posts")
    ref = props.containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    val activeFilterTopic =
        props.selectedTopic
            ?.let { topic ->
                props.content.state.topicFilters[topic.title]?.let { topic to it }
            }
            ?: props.content.state.posts.firstNotNullOfOrNull { topicPosts ->
                props.content.state.topicFilters[topicPosts.topic.title]?.let { topicPosts.topic to it }
            }
    div {
        className = ClassName("topic-board__column-header")
        if (props.showBackToTopics) {
            button {
                className = ClassName("topic-board__back-button")
                ariaLabel = translator.translate("connected.mobile.backToTopics")
                onClick = { props.onBackToTopics() }
                i {
                    className = ClassName("fa fa-solid fa-chevron-left topic-board__back-icon")
                }
                span {
                    className = ClassName("topic-board__back-label")
                    +translator.translate("connected.mobile.backToTopics")
                }
            }
        }
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.posts.title")
        }
        div {
            className = ClassName("topic-board__column-actions")
            if (activeFilterTopic != null) {
                val (filterTopic, filterScore) = activeFilterTopic
                val filterLabel =
                    translator.translate(
                        "connected.posts.filter.active",
                        mapOf("value" to formatSimilarity(filterScore)),
                    )
                val clearLabel = translator.translate("connected.posts.filter.clear")
                div {
                    className = ClassName("topic-board__filter-indicator")
                    button {
                        className = ClassName("topic-board__filter-reset")
                        ariaLabel = clearLabel
                        title = clearLabel
                        onClick = { props.content.actions.clearTopicFilter(filterTopic) }
                        i {
                            className = ClassName("fa fa-solid fa-arrows-up-to-line topic-board__filter-reset-icon")
                        }
                    }
                    span {
                        className = ClassName("topic-board__filter-value")
                        +filterLabel
                    }
                }
            }
        }
    }
    props.selectedTopic?.let { topicHighlightCard(translator, it) }
    when {
        props.content.state.isLoadingPosts -> Unit
        props.content.state.posts.isEmpty() ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.empty")
            }
        else ->
            props.content.state.posts.forEach { topicPosts ->
                val filterScore = props.content.state.topicFilters[topicPosts.topic.title]
                val filterResult = filterEntriesByScore(topicPosts.entries, filterScore)
                filterResult.entries.forEach { entry ->
                    postCard(
                        IdentityConnectedViewPostCardProps(
                            entryContext =
                                IdentityConnectedViewEntryContext(
                                    translator = translator,
                                    visualsFactory = props.visualsFactory,
                                    entry = entry,
                                    connectedGid = props.connectedGid,
                                    topic = topicPosts.topic,
                                ),
                            entryActions = props.buildEntryActions(entry),
                            topic = topicPosts.topic,
                            isFilterEntry = filterResult.filteredEntryId == entry.id,
                            onToggleFilter = props.content.actions.toggleTopicFilter,
                            isSelected = props.selectedPost?.id == entry.id,
                            onSelect = {
                                props.content.actions.clearDeleteCandidate()
                                props.content.actions.selectPost(entry)
                            },
                        ),
                    )
                }
            }
    }
    if (!props.content.state.isLoadingPosts && props.content.state.posts.isNotEmpty()) {
        div {
            ref = props.loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
}

private fun IdentityConnectedViewPostsColumnProps.buildEntryActions(entry: QueryResult.Entry): IdentityConnectedViewEntryActions =
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

private fun ChildrenBuilder.postCard(
    props: IdentityConnectedViewPostCardProps,
) {
    val entryContext = props.entryContext
    val entryActions = props.entryActions
    val authorVisuals = entryContext.visualsFactory.createFromGid(entryContext.entry.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(entryContext.entry.storedAt)
    val similarity = formatSimilarity(entryContext.entry.similarity)
    val canOpenAdminUserCard =
        entryActions.adminContact.canComposeAdminMessage &&
            entryContext.connectedGid != null &&
            entryContext.entry.authorGID != entryContext.connectedGid
    val canDeletePost =
        (entryContext.connectedGid != null && entryContext.entry.authorGID == entryContext.connectedGid) ||
            entryActions.canDeleteAsAdmin
    val showDeletePopover = canDeletePost && entryActions.deleteCandidateId == entryContext.entry.id
    val showComplaintComposer = entryActions.complaintComposerPostId == entryContext.entry.id
    val cardClass =
        buildString {
            append("post-card")
            if (props.isFilterEntry) append(" post-card--dimmed")
            if (props.isSelected) append(" post-card--selected")
        }
    val filterLabel =
        if (props.isFilterEntry) {
            entryContext.translator.translate("connected.posts.filter.clear")
        } else {
            entryContext.translator.translate("connected.posts.filter.apply")
        }
    div {
        key = entryContext.entry.id.unsafeCast<Key>()
        className = ClassName(cardClass)
        asDynamic()["data-post-id"] = entryContext.entry.id
        onClick = { props.onSelect() }
        div {
            className = ClassName("post-card__author-row")
            renderEntryAuthor(
                translator = entryContext.translator,
                entryId = entryContext.entry.id,
                entryAuthorGid = entryContext.entry.authorGID,
                authorName = authorName,
                avatar = authorVisuals.avatar,
                timestamp = timestamp,
                avatarClassName = "post-card__avatar",
                detailsClassName = "post-card__author-details",
                authorNameClassName = "post-card__author-name",
                timestampClassName = "post-card__timestamp",
                canOpenAdminUserCard = canOpenAdminUserCard,
                onToggleAdminUserCard = entryActions.adminContact.onToggleAdminUserCard,
            )
            div {
                className = ClassName("post-card__meta")
                span {
                    className = ClassName("post-card__similarity")
                    +similarity
                }
                button {
                    className =
                        if (props.isFilterEntry) {
                            ClassName("post-card__filter post-card__filter--active")
                        } else {
                            ClassName("post-card__filter")
                        }
                    ariaLabel = filterLabel
                    title = filterLabel
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        props.onToggleFilter(props.topic, entryContext.entry)
                    }
                    i {
                        className = ClassName("fa fa-solid fa-arrows-up-to-line")
                    }
                }
                if (canDeletePost) {
                    val deleteLabel = entryContext.translator.translate("connected.posts.delete.action")
                    button {
                        className = ClassName("post-card__delete")
                        ariaLabel = deleteLabel
                        title = deleteLabel
                        onClick = { event: MouseEvent<*, *> ->
                            event.stopPropagation()
                            entryActions.onRequestDelete()
                        }
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
                        onClick = { event: MouseEvent<*, *> ->
                            event.stopPropagation()
                            entryActions.onRequestComplaint()
                        }
                        i {
                            className = ClassName("fa fa-regular fa-flag")
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("post-card__content")
            +entryContext.entry.content
        }
        if (showComplaintComposer) {
            div {
                className = ClassName("post-card__delete-popover complaint-composer complaint-composer--card")
                onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
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
                onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
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

internal fun ChildrenBuilder.renderEntryAuthor(
    translator: Translator,
    entryId: String,
    entryAuthorGid: String,
    authorName: String,
    avatar: String,
    timestamp: String?,
    avatarClassName: String,
    detailsClassName: String,
    authorNameClassName: String,
    timestampClassName: String,
    canOpenAdminUserCard: Boolean,
    onToggleAdminUserCard: (String, String, String, String) -> Unit,
) {
    if (canOpenAdminUserCard) {
        button {
            className = ClassName("admin-user-trigger")
            ariaLabel = translator.translate("connected.messages.actions.open")
            title = translator.translate("connected.messages.actions.open")
            onClick = { event: MouseEvent<*, *> ->
                event.stopPropagation()
                onToggleAdminUserCard(entryId, entryAuthorGid, authorName, avatar)
            }
            img {
                className = ClassName(avatarClassName)
                src = avatar
                alt = authorName
            }
            div {
                className = ClassName(detailsClassName)
                span {
                    className = ClassName(authorNameClassName)
                    +authorName
                }
                if (timestamp != null) {
                    span {
                        className = ClassName(timestampClassName)
                        +timestamp
                    }
                }
            }
        }
    } else {
        img {
            className = ClassName(avatarClassName)
            src = avatar
            alt = authorName
        }
        div {
            className = ClassName(detailsClassName)
            span {
                className = ClassName(authorNameClassName)
                +authorName
            }
            if (timestamp != null) {
                span {
                    className = ClassName(timestampClassName)
                    +timestamp
                }
            }
        }
    }
}
