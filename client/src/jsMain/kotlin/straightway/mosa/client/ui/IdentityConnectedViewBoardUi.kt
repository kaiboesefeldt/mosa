package straightway.mosa.client.ui

import react.ChildrenBuilder
import react.RefObject
import react.dom.html.ReactHTML.div
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName
import web.html.HTMLDivElement

internal fun ChildrenBuilder.connectedContentBoard(
    translator: Translator,
    profile: IdentityScreen.Profile,
    visualsFactory: IdentityVisualsFactory,
    adminPresentation: IdentityAdminPresentation,
    viewport: IdentityConnectedViewViewportController,
    shell: IdentityConnectedViewMobileShell,
    content: IdentityConnectedViewContentController,
    moderation: IdentityConnectedViewModerationController,
    admin: IdentityConnectedViewAdminController,
    uiState: IdentityConnectedViewUiState,
    postsContainerRef: RefObject<HTMLDivElement>,
    loadMoreRef: RefObject<HTMLDivElement>,
    repliesContainerRef: RefObject<HTMLDivElement>,
    repliesLoadMoreRef: RefObject<HTMLDivElement>,
) = div {
    className = ClassName(shell.boardClass)
    div {
        className = ClassName("topic-board__track")
        if (viewport.isMobile) {
            asDynamic().style = mobileTrackStyle(shell.combinedShiftPercent)
            onTouchStart = shell.onTouchStart
            onTouchMove = shell.onTouchMove
            onTouchEnd = shell.onTouchEnd
            onTouchCancel = shell.onTouchEnd
        }
        topicColumn(translator, content.topics, content.selectedTopic, content.addTopic, content.selectTopic, content.removeTopic)
        postsColumn(
            translator = translator,
            visualsFactory = visualsFactory,
            posts = content.posts,
            selectedTopic = content.selectedTopic,
            selectedPost = content.selectedPost,
            isLoading = content.isLoadingPosts,
            activeFilters = content.topicFilters,
            onToggleFilter = content.toggleTopicFilter,
            onClearFilter = content.clearTopicFilter,
            onSelectPost = content.selectPost,
            connectedGid = profile.connectedIdentityGid,
            canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
            deleteCandidateId = content.deleteCandidate?.id,
            isDeleting = content.isDeletingPost,
            deleteError = content.deleteError,
            onRequestDelete = content.requestDelete,
            onCancelDelete = content.clearDeleteCandidate,
            onConfirmDelete = content.confirmDelete,
            complaintActionPostId = moderation.complaintActionPostId,
            complaintComposerPostId = moderation.complaintComposerPostId,
            complaintComposerText = moderation.complaintComposerText,
            complaintComposerError = moderation.complaintComposerError,
            isComplaintSubmitting = moderation.isComplaintSubmitting,
            onRequestComplaint = moderation.requestPostComplaint,
            onComplaintTextChange = moderation.setComplaintComposerText,
            onCloseComplaintComposer = moderation.closeComplaintComposer,
            onSubmitComplaint = moderation.submitPostComplaint,
            canComposeAdminMessage = adminPresentation.canDeleteAsAdmin,
            adminUserCardAnchorId = admin.adminUserCardAnchorId,
            adminMessageRecipientName = admin.adminMessageRecipientName,
            adminMessageSubject = admin.adminMessageSubject,
            adminMessageBody = admin.adminMessageBody,
            isAdminMessageSending = admin.isAdminMessageSending,
            adminMessageError = admin.adminMessageError,
            isAdminMessageComposerOpen = admin.isAdminMessageComposerOpen,
            onToggleAdminUserCard = admin.toggleAdminUserCard,
            onOpenAdminMessageComposer = admin.openAdminMessageCompose,
            onAdminMessageSubjectChange = admin.setAdminMessageSubject,
            onAdminMessageBodyChange = admin.setAdminMessageBody,
            onCloseAdminMessageComposer = admin.closeAdminUserCard,
            onSendAdminMessage = admin.sendAdminMessageToCurrentRecipient,
            showBackToTopics = shell.showBackButtons && viewport.mobilePane != MobilePane.Topics,
            onBackToTopics = { viewport.setMobilePane(MobilePane.Topics) },
            containerRef = postsContainerRef,
            loadMoreRef = loadMoreRef,
        )
        repliesColumn(
            translator = translator,
            visualsFactory = visualsFactory,
            selectedPost = content.selectedPost,
            replies = content.replies,
            isLoading = content.isLoadingReplies,
            connectedGid = profile.connectedIdentityGid,
            canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
            deleteCandidateId = content.deleteCandidate?.id,
            isDeleting = content.isDeletingPost,
            deleteError = content.deleteError,
            onRequestDelete = content.requestDelete,
            onCancelDelete = content.clearDeleteCandidate,
            onConfirmDelete = content.confirmDelete,
            complaintActionPostId = moderation.complaintActionPostId,
            complaintComposerPostId = moderation.complaintComposerPostId,
            complaintComposerText = moderation.complaintComposerText,
            complaintComposerError = moderation.complaintComposerError,
            isComplaintSubmitting = moderation.isComplaintSubmitting,
            onRequestComplaint = moderation.requestPostComplaint,
            onComplaintTextChange = moderation.setComplaintComposerText,
            onCloseComplaintComposer = moderation.closeComplaintComposer,
            onSubmitComplaint = moderation.submitPostComplaint,
            isMobile = viewport.isMobile,
            showBackToPosts = shell.showBackButtons,
            onBackToPosts = {
                if (content.selectedTopic == null) viewport.setMobilePane(MobilePane.Topics) else viewport.setMobilePane(MobilePane.Posts)
            },
            containerRef = repliesContainerRef,
            loadMoreRef = repliesLoadMoreRef,
            isComposerOpen = content.isReplyComposerOpen,
            replyText = content.replyComposerText,
            canActivateComposer = content.selectedPost != null && !content.isReplyPublishing,
            isPublishing = content.isReplyPublishing,
            errorText = content.replyPublishError,
            onOpenComposer = content.openReplyComposer,
            onCloseComposer = content.closeReplyComposer,
            onReplyTextChange = content.setReplyComposerText,
            onSendReply = content.sendReply,
            canComposeAdminMessage = adminPresentation.canDeleteAsAdmin,
            adminUserCardAnchorId = admin.adminUserCardAnchorId,
            adminMessageRecipientName = admin.adminMessageRecipientName,
            adminMessageSubject = admin.adminMessageSubject,
            adminMessageBody = admin.adminMessageBody,
            isAdminMessageSending = admin.isAdminMessageSending,
            adminMessageError = admin.adminMessageError,
            isAdminMessageComposerOpen = admin.isAdminMessageComposerOpen,
            onToggleAdminUserCard = admin.toggleAdminUserCard,
            onOpenAdminMessageComposer = admin.openAdminMessageCompose,
            onAdminMessageSubjectChange = admin.setAdminMessageSubject,
            onAdminMessageBodyChange = admin.setAdminMessageBody,
            onCloseAdminMessageComposer = admin.closeAdminUserCard,
            onSendAdminMessage = admin.sendAdminMessageToCurrentRecipient,
        )
    }
    if (uiState.shouldRenderFloatingComposer) {
        floatingComposerButton(
            label = translator.translate("connected.composer.open"),
            onActivate = content.openComposer,
        )
    }
}

private fun mobileTrackStyle(combinedShiftPercent: Double): dynamic {
    val style = js("({})")
    style["--mobile-shift"] = "${combinedShiftPercent}%"
    return style
}
