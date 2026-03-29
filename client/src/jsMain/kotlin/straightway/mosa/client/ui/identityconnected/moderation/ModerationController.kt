package straightway.mosa.client.ui.identityconnected.moderation

internal fun useModerationController(dependencies: ModerationControllerDependencies): ModerationController {
    val inboxController =
        useModerationInboxController(
            ModerationInboxControllerDependencies(
                context = dependencies.context,
                inboxStateStore = dependencies.inboxStateStore,
                adminMessageInboxService = dependencies.adminMessageInboxService,
                podBaseProvider = dependencies.podBaseProvider,
                connectedIdentityGid = dependencies.connectedIdentityGid,
            ),
        )
    val complaintController =
        useModerationComplaintQueueController(
            dependencies =
                ModerationComplaintQueueControllerDependencies(
                    context = dependencies.context,
                    postComplaintService = dependencies.postComplaintService,
                    adminPostComplaintService = dependencies.adminPostComplaintService,
                    connectedIdentityGid = dependencies.connectedIdentityGid,
                    showsConnectedAdminBadge = dependencies.showsConnectedAdminBadge,
                ),
            refreshInbox = inboxController.actions.refreshInbox,
        )

    useModerationPopoverDismiss(
        isInboxOpenRef = inboxController.refs.isOpenRef,
        isComplaintQueueOpenRef = complaintController.refs.isOpenRef,
        inboxButtonRef = inboxController.refs.buttonRef,
        complaintQueueButtonRef = complaintController.refs.buttonRef,
        inboxPopoverRef = inboxController.refs.popoverRef,
        complaintQueuePopoverRef = complaintController.refs.popoverRef,
        closeInbox = inboxController.actions.closeInbox,
        closeComplaintQueue = complaintController.actions.closeComplaintQueue,
    )

    return ModerationController(
        state =
            ModerationControllerState(
                isInboxOpen = inboxController.state.isOpen,
                isInboxLoading = inboxController.state.isLoading,
                isInboxAppending = inboxController.state.isAppending,
                inboxEntries = inboxController.state.entries,
                showSeenInboxEntries = inboxController.state.showSeenEntries,
                hiddenSeenInboxMessageIds = inboxController.state.hiddenSeenMessageIds,
                inboxError = inboxController.state.error,
                inboxHasMore = inboxController.state.hasMore,
                seenInboxMessageIds = inboxController.state.seenMessageIds,
                unreadInboxCount = inboxController.state.unreadCount,
                isComplaintQueueOpen = complaintController.state.isOpen,
                isComplaintQueueLoading = complaintController.state.isLoading,
                isComplaintQueueAppending = complaintController.state.isAppending,
                complaintQueueEntries = complaintController.state.entries,
                complaintQueueError = complaintController.state.error,
                complaintQueueHasMore = complaintController.state.hasMore,
                showProcessedComplaintEntries = complaintController.state.showProcessedEntries,
                openComplaintCount = complaintController.state.openCount,
                complaintActionPostId = complaintController.state.actionPostId,
                complaintComposerPostId = complaintController.state.composerPostId,
                complaintComposerText = complaintController.state.composerText,
                complaintComposerError = complaintController.state.composerError,
                isComplaintSubmitting = complaintController.state.isSubmitting,
                complaintDecisionEntryId = complaintController.state.decisionEntryId,
                complaintDecisionTargetStatus = complaintController.state.decisionTargetStatus,
                complaintDecisionText = complaintController.state.decisionText,
                complaintDecisionError = complaintController.state.decisionError,
                isComplaintDecisionSubmitting = complaintController.state.isDecisionSubmitting,
            ),
        refs =
            ModerationControllerRefs(
                inboxButtonRef = inboxController.refs.buttonRef,
                complaintQueueButtonRef = complaintController.refs.buttonRef,
                inboxPopoverRef = inboxController.refs.popoverRef,
                complaintQueuePopoverRef = complaintController.refs.popoverRef,
            ),
        actions =
            ModerationControllerActions(
                toggleSeenInboxVisibility = inboxController.actions.toggleSeenVisibility,
                loadInbox = inboxController.actions.loadInbox,
                closeInbox = inboxController.actions.closeInbox,
                loadMoreInbox = inboxController.actions.loadMoreInbox,
                loadComplaintQueue = complaintController.actions.loadComplaintQueue,
                closeComplaintQueue = complaintController.actions.closeComplaintQueue,
                toggleProcessedComplaintVisibility = complaintController.actions.toggleProcessedComplaintVisibility,
                loadMoreComplaintQueue = complaintController.actions.loadMoreComplaintQueue,
                requestPostComplaint = complaintController.actions.requestPostComplaint,
                setComplaintComposerText = complaintController.actions.setComplaintComposerText,
                closeComplaintComposer = complaintController.actions.closeComplaintComposer,
                submitPostComplaint = complaintController.actions.submitPostComplaint,
                startComplaintDecision = complaintController.actions.startComplaintDecision,
                setComplaintDecisionText = complaintController.actions.setComplaintDecisionText,
                closeComplaintDecisionComposer = complaintController.actions.closeComplaintDecisionComposer,
                submitComplaintDecision = complaintController.actions.submitComplaintDecision,
            ),
    )
}
