@file:OptIn(ExperimentalWasmJsInterop::class)

package straightway.mosa.client.ui

import kotlin.js.unsafeCast
import kotlin.js.ExperimentalWasmJsInterop
import react.ChildrenBuilder
import react.Key
import react.Ref
import react.dom.events.MouseEvent
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.i18n.Translator
import straightway.mosa.content.QueryResult
import web.cssom.ClassName
import web.html.HTMLDivElement

internal fun ChildrenBuilder.topicColumn(
    translator: Translator,
    topics: List<Topic>,
    selectedTopic: Topic?,
    onAddTopic: () -> Unit,
    onSelectTopic: (Topic) -> Unit,
    onRemoveTopic: (Topic) -> Unit,
) = div {
    className = ClassName("topic-board__column topic-board__column--topics")
    div {
        className = ClassName("topic-board__column-header")
        span {
            className = ClassName("topic-board__column-title")
            +translator.translate("connected.topics.title")
        }
        button {
            className = ClassName("button button--primary topic-board__add-button")
            onClick = { onAddTopic() }
            +translator.translate("connected.topics.add")
        }
    }
    if (topics.isEmpty()) {
        div {
            className = ClassName("topic-board__empty")
            +translator.translate("connected.topics.empty")
        }
    } else {
        topics.forEach { topic ->
            val cardClass =
                if (topic == selectedTopic) {
                    "topic-card topic-card--selected"
                } else {
                    "topic-card"
                }
            div {
                key = topic.title.unsafeCast<Key>()
                className = ClassName(cardClass)
                asDynamic()["data-topic-title"] = topic.title
                onClick = { onSelectTopic(topic) }
                button {
                    className = ClassName("topic-card__delete")
                    ariaLabel = translator.translate("connected.topics.delete")
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        onRemoveTopic(topic)
                    }
                    +"×"
                }
                span {
                    className = ClassName("topic-card__title")
                    +topic.title
                }
            }
        }
    }
}

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
                props.content.topicFilters[topic.title]?.let { topic to it }
            }
            ?: props.content.posts.firstNotNullOfOrNull { topicPosts ->
                props.content.topicFilters[topicPosts.topic.title]?.let { topicPosts.topic to it }
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
                        onClick = { props.content.clearTopicFilter(filterTopic) }
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
    val highlightTopic = props.selectedTopic
    if (highlightTopic != null) {
        topicHighlightCard(translator, highlightTopic)
    }
    when {
        props.content.isLoadingPosts -> Unit
        props.content.posts.isEmpty() ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.empty")
            }
        else ->
            props.content.posts.forEach { topicPosts ->
                val filterScore = props.content.topicFilters[topicPosts.topic.title]
                val filterResult = filterEntriesByScore(topicPosts.entries, filterScore)
                filterResult.entries.forEach { entry ->
                    val isFilterEntry = filterResult.filteredEntryId == entry.id
                    postCard(
                        translator = translator,
                        visualsFactory = props.visualsFactory,
                        entry = entry,
                        topic = topicPosts.topic,
                        isFilterEntry = isFilterEntry,
                        onToggleFilter = props.content.toggleTopicFilter,
                        isSelected = props.selectedPost?.id == entry.id,
                        onSelect = {
                            props.content.clearDeleteCandidate()
                            props.content.selectPost(entry)
                        },
                        connectedGid = props.connectedGid,
                        canDeleteAsAdmin = props.canDeleteAsAdmin,
                        deleteCandidateId = props.content.deleteCandidate?.id,
                        isDeleting = props.content.isDeletingPost,
                        deleteError = props.content.deleteError,
                        onRequestDelete = { props.content.requestDelete(entry) },
                        onCancelDelete = props.content.clearDeleteCandidate,
                        onConfirmDelete = { props.content.confirmDelete(entry) },
                        canComplain = props.connectedGid != null && entry.authorGID != props.connectedGid,
                        isComplaining = props.moderation.complaintActionPostId == entry.id,
                        complaintComposerPostId = props.moderation.complaintComposerPostId,
                        complaintComposerText = props.moderation.complaintComposerText,
                        complaintComposerError = props.moderation.complaintComposerError,
                        isComplaintSubmitting = props.moderation.isComplaintSubmitting,
                        onRequestComplaint = { props.moderation.requestPostComplaint(entry) },
                        onComplaintTextChange = props.moderation.setComplaintComposerText,
                        onCloseComplaintComposer = props.moderation.closeComplaintComposer,
                        onSubmitComplaint = props.moderation.submitPostComplaint,
                        canComposeAdminMessage = props.canDeleteAsAdmin,
                        onToggleAdminUserCard = props.admin.toggleAdminUserCard,
                    )
                }
            }
    }
    if (!props.content.isLoadingPosts && props.content.posts.isNotEmpty()) {
        div {
            ref = props.loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
}

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
        props.content.isLoadingReplies && props.content.replies == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.loading")
            }
        else -> {
            replyOriginCard(translator, props.visualsFactory, props.selectedPost)
            val replyEntries = props.content.replies?.entries.orEmpty()
            if (replyEntries.isEmpty() && !props.content.isLoadingReplies) {
                div {
                    className = ClassName("topic-board__empty")
                    +translator.translate("connected.replies.none")
                }
            }
            replyEntries.forEach { reply ->
                replyCard(
                    translator = translator,
                    visualsFactory = props.visualsFactory,
                    reply = reply,
                    connectedGid = props.connectedGid,
                    canDeleteAsAdmin = props.canDeleteAsAdmin,
                    deleteCandidateId = props.content.deleteCandidate?.id,
                    isDeleting = props.content.isDeletingPost,
                    deleteError = props.content.deleteError,
                    onRequestDelete = { props.content.requestDelete(reply) },
                    onCancelDelete = props.content.clearDeleteCandidate,
                    onConfirmDelete = { props.content.confirmDelete(reply) },
                    canComplain = props.connectedGid != null && reply.authorGID != props.connectedGid,
                    isComplaining = props.moderation.complaintActionPostId == reply.id,
                    complaintComposerPostId = props.moderation.complaintComposerPostId,
                    complaintComposerText = props.moderation.complaintComposerText,
                    complaintComposerError = props.moderation.complaintComposerError,
                    isComplaintSubmitting = props.moderation.isComplaintSubmitting,
                    onRequestComplaint = { props.moderation.requestPostComplaint(reply) },
                    onComplaintTextChange = props.moderation.setComplaintComposerText,
                    onCloseComplaintComposer = props.moderation.closeComplaintComposer,
                    onSubmitComplaint = props.moderation.submitPostComplaint,
                    canComposeAdminMessage = props.canDeleteAsAdmin,
                    onToggleAdminUserCard = props.admin.toggleAdminUserCard,
                )
            }
        }
    }
    if (!props.content.isLoadingReplies && props.content.replies != null && props.content.replies.entries.isNotEmpty()) {
        div {
            ref = props.loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
    div {
        className = ClassName("reply-composer-wrapper")
        div {
        className = ClassName("reply-composer-layer")
            if (props.content.isReplyComposerOpen) {
                replyComposerPopover(
                    translator = translator,
                    text = props.content.replyComposerText,
                    isPublishing = props.content.isReplyPublishing,
                    errorText = props.content.replyPublishError,
                    onTextChange = props.content.setReplyComposerText,
                    onSend = props.content.sendReply,
                    onClose = props.content.closeReplyComposer,
                )
            }
            div {
                className = ClassName("reply-composer-fab")
                replyComposerButton(
                    label = translator.translate("connected.replies.compose.open"),
                    disabled = props.selectedPost == null || props.content.isReplyPublishing,
                    onActivate = props.content.openReplyComposer,
                )
            }
        }
    }
}

private fun ChildrenBuilder.replyCard(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    reply: QueryResult.Entry,
    connectedGid: String?,
    canDeleteAsAdmin: Boolean,
    deleteCandidateId: String?,
    isDeleting: Boolean,
    deleteError: String?,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    canComplain: Boolean,
    isComplaining: Boolean,
    complaintComposerPostId: String?,
    complaintComposerText: String,
    complaintComposerError: String?,
    isComplaintSubmitting: Boolean,
    onRequestComplaint: () -> Unit,
    onComplaintTextChange: (String) -> Unit,
    onCloseComplaintComposer: () -> Unit,
    onSubmitComplaint: () -> Unit,
    canComposeAdminMessage: Boolean,
    onToggleAdminUserCard: (String, String, String, String) -> Unit,
) {
    val authorVisuals = visualsFactory.createFromGid(reply.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val canOpenAdminUserCard = canComposeAdminMessage && connectedGid != null && reply.authorGID != connectedGid
    val canDeleteReply = (connectedGid != null && reply.authorGID == connectedGid) || canDeleteAsAdmin
    val showDeletePopover = canDeleteReply && deleteCandidateId == reply.id
    val showComplaintComposer = complaintComposerPostId == reply.id
    div {
        key = reply.id.unsafeCast<Key>()
        className = ClassName("reply-card")
        div {
            className = ClassName("reply-card__author-row")
            if (canOpenAdminUserCard) {
                button {
                    className = ClassName("admin-user-trigger")
                    ariaLabel = translator.translate("connected.messages.actions.open")
                    title = translator.translate("connected.messages.actions.open")
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        onToggleAdminUserCard(reply.id, reply.authorGID, authorName, authorVisuals.avatar)
                    }
                    img {
                        className = ClassName("reply-card__avatar")
                        src = authorVisuals.avatar
                        alt = authorName
                    }
                    div {
                        className = ClassName("reply-card__details")
                        span {
                            className = ClassName("reply-card__author-name")
                            +authorName
                        }
                    }
                }
            }
            if (!canOpenAdminUserCard) {
                img {
                    className = ClassName("reply-card__avatar")
                    src = authorVisuals.avatar
                    alt = authorName
                }
                div {
                    span {
                        className = ClassName("reply-card__author-name")
                        +authorName
                    }
                }
            }
            div {
                className = ClassName("reply-card__meta")
                span {
                    className = ClassName("reply-card__timestamp")
                    +formatTimestamp(reply.storedAt)
                }
                if (canDeleteReply) {
                    val deleteLabel = translator.translate("connected.posts.delete.action")
                    button {
                        className = ClassName("post-card__delete reply-card__delete")
                        ariaLabel = deleteLabel
                        title = deleteLabel
                        onClick = { onRequestDelete() }
                        i {
                            className = ClassName("fa fa-solid fa-trash")
                        }
                    }
                }
                if (canComplain) {
                    val complaintLabel = translator.translate("connected.complaints.post.action")
                    button {
                        className = ClassName("post-card__filter")
                        disabled = isComplaining
                        ariaLabel = complaintLabel
                        title = complaintLabel
                        onClick = {
                            onRequestComplaint()
                        }
                        i {
                            className = ClassName("fa fa-regular fa-flag")
                        }
                    }
                }
            }
        }
        div {
            className = ClassName("reply-card__content")
            +reply.content
        }
        if (showComplaintComposer) {
            div {
                className = ClassName("post-card__delete-popover complaint-composer complaint-composer--card")
                complaintComposerPopover(
                    translator = translator,
                    title = translator.translate("connected.complaints.post.title"),
                    placeholder = translator.translate("connected.complaints.post.placeholder"),
                    text = complaintComposerText,
                    isSubmitting = isComplaintSubmitting,
                    errorText = complaintComposerError,
                    sendLabel = translator.translate("connected.complaints.post.send"),
                    onTextChange = onComplaintTextChange,
                    onSend = onSubmitComplaint,
                    onClose = onCloseComplaintComposer,
                )
            }
        }
        if (showDeletePopover) {
            val cancelLabel = translator.translate("connected.posts.delete.cancel")
            val confirmLabel = translator.translate("connected.posts.delete.confirm")
            div {
                className = ClassName("post-card__delete-popover")
                span {
                    className = ClassName("post-card__delete-title")
                    +translator.translate("connected.posts.delete.title")
                }
                div {
                    className = ClassName("post-card__delete-content")
                    +reply.content
                }
                if (deleteError != null) {
                    div {
                        className = ClassName("post-card__delete-error")
                        +deleteError
                    }
                }
                div {
                    className = ClassName("post-card__delete-actions")
                    button {
                        className = ClassName("button button--secondary post-card__delete-cancel")
                        disabled = isDeleting
                        onClick = { onCancelDelete() }
                        +cancelLabel
                    }
                    button {
                        className = ClassName("button button--danger post-card__delete-confirm")
                        disabled = isDeleting
                        onClick = { onConfirmDelete() }
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

private fun ChildrenBuilder.topicHighlightCard(
    translator: Translator,
    topic: Topic,
) {
    div {
        className = ClassName("topic-highlight")
        span {
            className = ClassName("topic-highlight__badge")
            +translator.translate("connected.posts.selectedTopic")
        }
        span {
            className = ClassName("topic-highlight__title")
            +topic.title
        }
    }
}

private fun ChildrenBuilder.postCard(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    entry: QueryResult.Entry,
    topic: Topic,
    isFilterEntry: Boolean,
    onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    isSelected: Boolean,
    onSelect: () -> Unit,
    connectedGid: String?,
    canDeleteAsAdmin: Boolean,
    deleteCandidateId: String?,
    isDeleting: Boolean,
    deleteError: String?,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    canComplain: Boolean,
    isComplaining: Boolean,
    complaintComposerPostId: String?,
    complaintComposerText: String,
    complaintComposerError: String?,
    isComplaintSubmitting: Boolean,
    onRequestComplaint: () -> Unit,
    onComplaintTextChange: (String) -> Unit,
    onCloseComplaintComposer: () -> Unit,
    onSubmitComplaint: () -> Unit,
    canComposeAdminMessage: Boolean,
    onToggleAdminUserCard: (String, String, String, String) -> Unit,
) {
    val authorVisuals = visualsFactory.createFromGid(entry.authorGID)
    val authorName = authorVisuals.name.toString().trim()
    val timestamp = formatTimestamp(entry.storedAt)
    val similarity = formatSimilarity(entry.similarity)
    val canOpenAdminUserCard = canComposeAdminMessage && connectedGid != null && entry.authorGID != connectedGid
    val canDeletePost = (connectedGid != null && entry.authorGID == connectedGid) || canDeleteAsAdmin
    val showDeletePopover = canDeletePost && deleteCandidateId == entry.id
    val showComplaintComposer = complaintComposerPostId == entry.id
    val cardClass =
        buildString {
            append("post-card")
            if (isFilterEntry) append(" post-card--dimmed")
            if (isSelected) append(" post-card--selected")
        }
    val filterLabel =
        if (isFilterEntry) {
            translator.translate("connected.posts.filter.clear")
        } else {
            translator.translate("connected.posts.filter.apply")
        }
    div {
        key = entry.id.unsafeCast<Key>()
        className = ClassName(cardClass)
        asDynamic()["data-post-id"] = entry.id
        onClick = { onSelect() }
        div {
            className = ClassName("post-card__author-row")
            if (canOpenAdminUserCard) {
                button {
                    className = ClassName("admin-user-trigger")
                    ariaLabel = translator.translate("connected.messages.actions.open")
                    title = translator.translate("connected.messages.actions.open")
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        onToggleAdminUserCard(entry.id, entry.authorGID, authorName, authorVisuals.avatar)
                    }
                    img {
                        className = ClassName("post-card__avatar")
                        src = authorVisuals.avatar
                        alt = authorName
                    }
                    div {
                        className = ClassName("post-card__author-details")
                        span {
                            className = ClassName("post-card__author-name")
                            +authorName
                        }
                        span {
                            className = ClassName("post-card__timestamp")
                            +timestamp
                        }
                    }
                }
            }
            if (!canOpenAdminUserCard) {
                img {
                    className = ClassName("post-card__avatar")
                    src = authorVisuals.avatar
                    alt = authorName
                }
                div {
                    className = ClassName("post-card__author-details")
                    span {
                        className = ClassName("post-card__author-name")
                        +authorName
                    }
                    span {
                        className = ClassName("post-card__timestamp")
                        +timestamp
                    }
                }
            }
            div {
                className = ClassName("post-card__meta")
                span {
                    className = ClassName("post-card__similarity")
                    +similarity
                }
                button {
                    className =
                        if (isFilterEntry) {
                            ClassName("post-card__filter post-card__filter--active")
                        } else {
                            ClassName("post-card__filter")
                        }
                    ariaLabel = filterLabel
                    title = filterLabel
                    onClick = { event: MouseEvent<*, *> ->
                        event.stopPropagation()
                        onToggleFilter(topic, entry)
                    }
                    i {
                        className = ClassName("fa fa-solid fa-arrows-up-to-line")
                    }
                }
                if (canDeletePost) {
                    val deleteLabel = translator.translate("connected.posts.delete.action")
                    button {
                        className = ClassName("post-card__delete")
                        ariaLabel = deleteLabel
                        title = deleteLabel
                        onClick = { event: MouseEvent<*, *> ->
                            event.stopPropagation()
                            onRequestDelete()
                        }
                        i {
                            className = ClassName("fa fa-solid fa-trash")
                        }
                    }
                }
                if (canComplain) {
                    val complaintLabel = translator.translate("connected.complaints.post.action")
                    button {
                        className = ClassName("post-card__filter")
                        disabled = isComplaining
                        ariaLabel = complaintLabel
                        title = complaintLabel
                        onClick = { event: MouseEvent<*, *> ->
                            event.stopPropagation()
                            onRequestComplaint()
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
            +entry.content
        }
        if (showComplaintComposer) {
            div {
                className = ClassName("post-card__delete-popover complaint-composer complaint-composer--card")
                onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
                complaintComposerPopover(
                    translator = translator,
                    title = translator.translate("connected.complaints.post.title"),
                    placeholder = translator.translate("connected.complaints.post.placeholder"),
                    text = complaintComposerText,
                    isSubmitting = isComplaintSubmitting,
                    errorText = complaintComposerError,
                    sendLabel = translator.translate("connected.complaints.post.send"),
                    onTextChange = onComplaintTextChange,
                    onSend = onSubmitComplaint,
                    onClose = onCloseComplaintComposer,
                )
            }
        }
        if (showDeletePopover) {
            val cancelLabel = translator.translate("connected.posts.delete.cancel")
            val confirmLabel = translator.translate("connected.posts.delete.confirm")
            div {
                className = ClassName("post-card__delete-popover")
                onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
                span {
                    className = ClassName("post-card__delete-title")
                    +translator.translate("connected.posts.delete.title")
                }
                div {
                    className = ClassName("post-card__delete-content")
                    +entry.content
                }
                if (deleteError != null) {
                    div {
                        className = ClassName("post-card__delete-error")
                        +deleteError
                    }
                }
                div {
                    className = ClassName("post-card__delete-actions")
                    button {
                        className = ClassName("button button--secondary post-card__delete-cancel")
                        disabled = isDeleting
                        onClick = { onCancelDelete() }
                        +cancelLabel
                    }
                    button {
                        className = ClassName("button button--danger post-card__delete-confirm")
                        disabled = isDeleting
                        onClick = { onConfirmDelete() }
                        +confirmLabel
                    }
                }
            }
        }
    }
}
