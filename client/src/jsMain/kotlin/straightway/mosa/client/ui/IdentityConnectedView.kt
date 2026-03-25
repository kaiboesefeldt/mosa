package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.Date
import kotlin.js.unsafeCast
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.ChildrenBuilder
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.i
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.textarea
import react.dom.events.ChangeEvent
import react.dom.events.MouseEvent
import react.useEffect
import react.useMemo
import react.Ref
import react.useRef
import react.useState
import web.dom.Element
import web.html.HTMLDivElement
import straightway.mosa.client.assets.LogoResources
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.ContentCapabilitiesService
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.TopicFilters
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalNameGenerator
import web.cssom.ClassName
import web.html.HTMLTextAreaElement
import web.html.HTMLInputElement

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

val IdentityConnectedView = FC<IdentityConnectedViewProps> { props ->
    val translator = ConnectedViewInjector.get<Translator>()
    val repository = ConnectedViewInjector.get<TopicRepository>()
    val sessionStore = ConnectedViewInjector.get<IdentitySessionStore>()
    val inboxStateStore = ConnectedViewInjector.get<AdminMessageInboxStateStore>()
    val searchService = ConnectedViewInjector.get<TopicSearchService>()
    val postCache = ConnectedViewInjector.get<PostCache>()
    val replyCache = ConnectedViewInjector.get<ReplyCache>()
    val replySearchService = ConnectedViewInjector.get<ReplySearchService>()
    val nameGenerator = ConnectedViewInjector.get<FictionalNameGenerator>()
    val avatarGenerator = ConnectedViewInjector.get<DiceBearAvatarGenerator>()
    val postPublisher = ConnectedViewInjector.get<PostPublisher>()
    val contentCapabilitiesService = ConnectedViewInjector.get<ContentCapabilitiesService>()
    val postDeletionService = ConnectedViewInjector.get<PostDeletionService>()
    val allPostsDeletionService = ConnectedViewInjector.get<AllPostsDeletionService>()
    val replyPublisher = ConnectedViewInjector.get<ReplyPublisher>()
    val adminMessageHistoryService = ConnectedViewInjector.get<AdminMessageHistoryService>()
    val adminMessageInboxService = ConnectedViewInjector.get<AdminMessageInboxService>()
    val adminMessagePublisher = ConnectedViewInjector.get<AdminMessagePublisher>()
    val postComplaintService = ConnectedViewInjector.get<PostComplaintService>()
    val adminPostComplaintService = ConnectedViewInjector.get<AdminPostComplaintService>()
    val podBaseProvider = ConnectedViewInjector.get<PodBaseProvider>()
    val visualsFactory =
        useMemo(nameGenerator, avatarGenerator) {
            IdentityVisualsFactory(nameGenerator, avatarGenerator)
        }
    val scope = useMemo(*noDependencies) { MainScope() }
    val postsContainerRef = useRef<HTMLDivElement>()
    val loadMoreRef = useRef<HTMLDivElement>()
    val repliesContainerRef = useRef<HTMLDivElement>()
    val repliesLoadMoreRef = useRef<HTMLDivElement>()
    val (isProfileVisible, setProfileVisible) = useState(false)
    val (profileEntryAnimation, setProfileEntryAnimation) = useState(ProfileEntryAnimation.None)
    val profileEntryTimerRef = useRef<Int>()
    val (profileExitAnimation, setProfileExitAnimation) = useState(ProfileExitAnimation.None)
    val profileExitTimerRef = useRef<Int>()
    val profileAnimationDurationMs = 760
    val (toastMessage, setToastMessage) = useState<String?>(null)
    val toastTimerRef = useRef<Int>()
    val viewport = useIdentityConnectedViewViewportController()
    fun setPaneForSelectedTopic(topic: Topic?) {
        if (!viewport.isMobile) return
        viewport.setMobilePane(if (topic == null) MobilePane.Topics else MobilePane.Posts)
    }

    fun setPaneForSelectedPost(entry: QueryResult.Entry?) {
        if (!viewport.isMobile) return
        viewport.setMobilePane(if (entry == null) MobilePane.Posts else MobilePane.Replies)
    }

    useEffect(sessionStore) {
        scope.launch {
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            setProfileVisible(snapshot?.isProfileVisible ?: false)
        }
    }
    val adminPresentation =
        useIdentityAdminPresentation(
            connectedIdentityGid = props.profile.connectedIdentityGid,
            displayedIdentityGid = props.profile.identityGid,
            isProfileVisible = isProfileVisible,
            contentCapabilitiesService = contentCapabilitiesService,
            scope = scope,
        )
    suspend fun persistSessionSnapshot(
        builder: (IdentitySessionSnapshot?) -> IdentitySessionSnapshot,
    ) {
        val currentSnapshot = runCatching { sessionStore.load() }.getOrNull()
        sessionStore.save(builder(currentSnapshot))
    }

    fun clearToastTimer() {
        toastTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            toastTimerRef.current = null
        }
    }

    fun showToast(message: String) {
        clearToastTimer()
        setToastMessage(message)
        val timerId =
            window.setTimeout({
                setToastMessage(null)
            }, 3200)
        toastTimerRef.current = timerId
    }

    val content =
        useIdentityConnectedViewContentController(
            translator = translator,
            repository = repository,
            sessionStore = sessionStore,
            searchService = searchService,
            postCache = postCache,
            replyCache = replyCache,
            postPublisher = postPublisher,
            postDeletionService = postDeletionService,
            allPostsDeletionService = allPostsDeletionService,
            replyPublisher = replyPublisher,
            replySearchService = replySearchService,
            scope = scope,
            isMobile = viewport.isMobile,
            snapshotRefreshVersion = props.profile.snapshotRefreshVersion,
            postsContainerRef = postsContainerRef,
            repliesContainerRef = repliesContainerRef,
            showToast = ::showToast,
            persistSessionSnapshot = ::persistSessionSnapshot,
            onTopicPaneRequested = ::setPaneForSelectedTopic,
            onPostPaneRequested = ::setPaneForSelectedPost,
        )
    val topics = content.topics
    val selectedTopic = content.selectedTopic
    val posts = content.posts
    val selectedPost = content.selectedPost
    val replies = content.replies
    val isLoadingReplies = content.isLoadingReplies
    val topicFilters = content.topicFilters
    val isLoadingPosts = content.isLoadingPosts
    val isComposerOpen = content.isComposerOpen
    val composerText = content.composerText
    val isPublishing = content.isPublishing
    val publishError = content.publishError
    val deleteCandidate = content.deleteCandidate
    val isDeletingPost = content.isDeletingPost
    val isDeletingAllPosts = content.isDeletingAllPosts
    val deleteError = content.deleteError
    val isReplyComposerOpen = content.isReplyComposerOpen
    val replyComposerText = content.replyComposerText
    val isReplyPublishing = content.isReplyPublishing
    val replyPublishError = content.replyPublishError
    val moderation =
        useIdentityConnectedViewModerationController(
            translator = translator,
            inboxStateStore = inboxStateStore,
            adminMessageInboxService = adminMessageInboxService,
            postComplaintService = postComplaintService,
            adminPostComplaintService = adminPostComplaintService,
            podBaseProvider = podBaseProvider,
            scope = scope,
            connectedIdentityGid = props.profile.connectedIdentityGid,
            showsConnectedAdminBadge = adminPresentation.showsConnectedAdminBadge,
            showToast = ::showToast,
        )
    val isInboxOpen = moderation.isInboxOpen
    val isInboxLoading = moderation.isInboxLoading
    val isInboxAppending = moderation.isInboxAppending
    val visibleInboxEntries = moderation.visibleInboxEntries
    val inboxError = moderation.inboxError
    val inboxHasMore = moderation.inboxHasMore
    val showSeenInboxEntries = moderation.showSeenInboxEntries
    val hasHiddenSeenInboxEntries = moderation.hasHiddenSeenInboxEntries
    val seenInboxMessageIds = moderation.seenInboxMessageIds
    val hasReadInboxEntries = moderation.hasReadInboxEntries
    val unreadInboxCount = moderation.unreadInboxCount
    val isComplaintQueueOpen = moderation.isComplaintQueueOpen
    val isComplaintQueueLoading = moderation.isComplaintQueueLoading
    val isComplaintQueueAppending = moderation.isComplaintQueueAppending
    val visibleComplaintQueueEntries = moderation.visibleComplaintQueueEntries
    val complaintQueueError = moderation.complaintQueueError
    val complaintQueueHasMore = moderation.complaintQueueHasMore
    val showProcessedComplaintEntries = moderation.showProcessedComplaintEntries
    val hasHiddenProcessedComplaintEntries = moderation.hasHiddenProcessedComplaintEntries
    val openComplaintCount = moderation.openComplaintCount
    val complaintActionPostId = moderation.complaintActionPostId
    val complaintComposerPostId = moderation.complaintComposerPostId
    val complaintComposerText = moderation.complaintComposerText
    val complaintComposerError = moderation.complaintComposerError
    val isComplaintSubmitting = moderation.isComplaintSubmitting
    val complaintDecisionEntryId = moderation.complaintDecisionEntryId
    val complaintDecisionTargetStatus = moderation.complaintDecisionTargetStatus
    val complaintDecisionText = moderation.complaintDecisionText
    val complaintDecisionError = moderation.complaintDecisionError
    val isComplaintDecisionSubmitting = moderation.isComplaintDecisionSubmitting
    val inboxButtonRef = moderation.inboxButtonRef
    val complaintQueueButtonRef = moderation.complaintQueueButtonRef
    val inboxPopoverRef = moderation.inboxPopoverRef
    val complaintQueuePopoverRef = moderation.complaintQueuePopoverRef
    val admin =
        useIdentityConnectedViewAdminController(
            translator = translator,
            adminMessageHistoryService = adminMessageHistoryService,
            adminMessagePublisher = adminMessagePublisher,
            scope = scope,
            canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
            showToast = ::showToast,
        )
    val adminUserCardAnchorId = admin.adminUserCardAnchorId
    val adminMessageRecipientName = admin.adminMessageRecipientName
    val adminMessageRecipientAvatar = admin.adminMessageRecipientAvatar
    val adminMessageSubject = admin.adminMessageSubject
    val adminMessageBody = admin.adminMessageBody
    val isAdminMessageSending = admin.isAdminMessageSending
    val adminMessageError = admin.adminMessageError
    val isAdminMessageComposerOpen = admin.isAdminMessageComposerOpen
    val adminHistoryEntries = admin.adminHistoryEntries
    val isAdminHistoryLoading = admin.isAdminHistoryLoading
    val isAdminHistoryAppending = admin.isAdminHistoryAppending
    val adminHistoryError = admin.adminHistoryError
    val adminHistoryHasMore = admin.adminHistoryHasMore
    val adminHistoryListRef = admin.adminHistoryListRef
    val shell =
        useIdentityConnectedViewMobileShell(
            isMobile = viewport.isMobile,
            isTouchDevice = viewport.isTouchDevice,
            mobilePane = viewport.mobilePane,
            setMobilePane = viewport.setMobilePane,
            selectedTopic = selectedTopic,
            selectedPost = selectedPost,
            posts = posts,
            replies = replies,
            postsContainerRef = postsContainerRef,
            loadMoreRef = loadMoreRef,
            repliesContainerRef = repliesContainerRef,
            repliesLoadMoreRef = repliesLoadMoreRef,
            findTopicByTitle = content.findTopicByTitle,
            findPostById = content.findPostById,
            selectTopic = content.selectTopic,
            selectPost = content.selectPost,
            loadMorePosts = content.loadMorePosts,
            loadMoreReplies = content.loadMoreReplies,
        )

    fun clearProfileExitTimer() {
        profileExitTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            profileExitTimerRef.current = null
        }
    }

    fun clearProfileEntryTimer() {
        profileEntryTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            profileEntryTimerRef.current = null
        }
    }

    fun startProfileEntryAnimation() {
        clearProfileExitTimer()
        setProfileExitAnimation(ProfileExitAnimation.None)
        clearProfileEntryTimer()
        setProfileEntryAnimation(ProfileEntryAnimation.AvatarEmerge)
        val timerId = window.setTimeout({
            setProfileEntryAnimation(ProfileEntryAnimation.None)
        }, profileAnimationDurationMs)
        profileEntryTimerRef.current = timerId
    }

    useEffect(*noDependencies) {
        try {
            awaitCancellation()
        } finally {
            clearProfileExitTimer()
            clearProfileEntryTimer()
            clearToastTimer()
            scope.cancel()
        }
    }

    val avatarDescription = translator.translate(adminPresentation.avatarTitleKey)

    fun persistProfileVisibility(visible: Boolean) {
        if (visible) {
            clearProfileExitTimer()
            setProfileExitAnimation(ProfileExitAnimation.None)
        } else {
            clearProfileEntryTimer()
            setProfileEntryAnimation(ProfileEntryAnimation.None)
            clearProfileExitTimer()
            setProfileExitAnimation(ProfileExitAnimation.None)
        }
        setProfileVisible(visible)
        scope.launch {
            persistSessionSnapshot { snapshot ->
                IdentitySessionSnapshot(
                    connectedIdentityGid = snapshot?.connectedIdentityGid,
                    isProfileVisible = visible,
                    selectedTopicTitle = snapshot?.selectedTopicTitle,
                )
            }
        }
    }

    fun showProfile() {
        if (isProfileVisible) {
            if (!props.profile.isPersisted) {
                startProfileEntryAnimation()
            }
            return
        }
        startProfileEntryAnimation()
        persistProfileVisibility(true)
    }

    fun hideProfile() {
        clearProfileEntryTimer()
        setProfileEntryAnimation(ProfileEntryAnimation.None)
        clearProfileExitTimer()
        setProfileExitAnimation(ProfileExitAnimation.None)
        persistProfileVisibility(false)
    }

    fun hideProfileWithSuction() {
        if (!isProfileVisible) return
        if (profileExitAnimation == ProfileExitAnimation.AvatarSuction) return
        clearProfileExitTimer()
        clearProfileEntryTimer()
        setProfileEntryAnimation(ProfileEntryAnimation.None)
        setProfileExitAnimation(ProfileExitAnimation.AvatarSuction)
        val timerId = window.setTimeout({
            hideProfile()
        }, profileAnimationDurationMs)
        profileExitTimerRef.current = timerId
    }

    div {
        className = ClassName("connected-shell")
        div {
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
                    ref = inboxButtonRef
                    className = ClassName("connected-shell__inbox-button")
                    ariaLabel = translator.translate("connected.messages.inbox.button")
                    title = translator.translate("connected.messages.inbox.button")
                    onClick = {
                        if (isInboxOpen) {
                            moderation.closeInbox()
                        } else {
                            moderation.loadInbox()
                        }
                    }
                    i {
                        className = ClassName("fa fa-solid fa-bullhorn")
                    }
                    if (unreadInboxCount > 0) {
                        span {
                            className = ClassName("connected-shell__inbox-badge")
                            +unreadInboxCount.toString()
                        }
                    }
                }
                if (adminPresentation.showsConnectedAdminBadge) {
                    button {
                        ref = complaintQueueButtonRef
                        className = ClassName("connected-shell__inbox-button")
                        ariaLabel = translator.translate("connected.complaints.admin.button")
                        title = translator.translate("connected.complaints.admin.button")
                        onClick = {
                            if (isComplaintQueueOpen) {
                                moderation.closeComplaintQueue()
                            } else {
                                moderation.loadComplaintQueue()
                            }
                        }
                        i {
                            className = ClassName("fa fa-solid fa-shield-halved")
                        }
                        if (openComplaintCount > 0) {
                            span {
                                className = ClassName("connected-shell__inbox-badge")
                                +openComplaintCount.toString()
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
                            if (adminPresentation.showsConnectedAdminBadge) {
                                "connected-shell__avatar-button connected-shell__avatar-button--admin"
                            } else {
                                "connected-shell__avatar-button"
                            },
                        )
                    ariaLabel = translator.translate("connected.profile.avatar.aria")
                    title = avatarDescription
                    onClick = { showProfile() }
                    val avatar = props.profile.identityAvatar
                    if (avatar != null) {
                        img {
                            className = ClassName("connected-shell__avatar-image")
                            src = avatar
                            alt = avatarDescription
                        }
                    } else {
                        span {
                            className = ClassName("connected-shell__avatar-fallback")
                            +(props.identityName.firstOrNull()?.uppercaseChar()?.toString() ?: "?")
                        }
                    }
                    if (adminPresentation.showsConnectedAdminBadge) {
                        span {
                            className = ClassName("connected-shell__avatar-badge")
                            +translator.translate("identity.admin.badge")
                        }
                    }
                }
                if (isInboxOpen) {
                    adminMessageInboxPopover(
                        translator,
                        visualsFactory,
                        visibleInboxEntries,
                        isInboxLoading,
                        isInboxAppending,
                        inboxError,
                        inboxHasMore,
                        showSeenInboxEntries,
                        hasHiddenSeenInboxEntries,
                        seenInboxMessageIds,
                        hasReadInboxEntries,
                        inboxPopoverRef,
                        moderation.toggleSeenInboxVisibility,
                        moderation.closeInbox,
                        moderation.loadMoreInbox,
                    )
                }
                if (isComplaintQueueOpen) {
                    postComplaintQueuePopover(
                        translator = translator,
                        identityVisualsFactory = visualsFactory,
                        entries = visibleComplaintQueueEntries,
                        isLoading = isComplaintQueueLoading,
                        isAppending = isComplaintQueueAppending,
                        errorText = complaintQueueError,
                        hasMore = complaintQueueHasMore,
                        showProcessedEntries = showProcessedComplaintEntries,
                        hasHiddenProcessedEntries = hasHiddenProcessedComplaintEntries,
                        popoverRef = complaintQueuePopoverRef,
                        onToggleProcessedVisibility = moderation.toggleProcessedComplaintVisibility,
                        onClose = moderation.closeComplaintQueue,
                        onLoadMore = moderation.loadMoreComplaintQueue,
                        decisionEntryId = complaintDecisionEntryId,
                        decisionTargetStatus = complaintDecisionTargetStatus,
                        decisionText = complaintDecisionText,
                        decisionErrorText = complaintDecisionError,
                        isDecisionSubmitting = isComplaintDecisionSubmitting,
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
        div {
            val boardClass =
                shell.boardClass
            className = ClassName(boardClass)
            val combinedShiftPercent = shell.combinedShiftPercent
            val showBackButtons = shell.showBackButtons
            div {
                className = ClassName("topic-board__track")
                if (viewport.isMobile) {
                    val trackStyle: dynamic = js("({})")
                    trackStyle["--mobile-shift"] = "${combinedShiftPercent}%"
                    asDynamic().style = trackStyle
                }
                if (viewport.isMobile) {
                    onTouchStart = shell.onTouchStart
                    onTouchMove = shell.onTouchMove
                    onTouchEnd = shell.onTouchEnd
                    onTouchCancel = shell.onTouchEnd
                }
                topicColumn(translator, topics, selectedTopic, content.addTopic, content.selectTopic, content.removeTopic)
                postsColumn(
                    translator = translator,
                    visualsFactory = visualsFactory,
                    posts = posts,
                    selectedTopic = selectedTopic,
                    selectedPost = selectedPost,
                    isLoading = isLoadingPosts,
                    activeFilters = topicFilters,
                    onToggleFilter = content.toggleTopicFilter,
                    onClearFilter = content.clearTopicFilter,
                    onSelectPost = content.selectPost,
                    connectedGid = props.profile.connectedIdentityGid,
                    canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
                    deleteCandidateId = deleteCandidate?.id,
                    isDeleting = isDeletingPost,
                    deleteError = deleteError,
                    onRequestDelete = content.requestDelete,
                    onCancelDelete = content.clearDeleteCandidate,
                    onConfirmDelete = content.confirmDelete,
                    complaintActionPostId = complaintActionPostId,
                    complaintComposerPostId = complaintComposerPostId,
                    complaintComposerText = complaintComposerText,
                    complaintComposerError = complaintComposerError,
                    isComplaintSubmitting = isComplaintSubmitting,
                    onRequestComplaint = moderation.requestPostComplaint,
                    onComplaintTextChange = moderation.setComplaintComposerText,
                    onCloseComplaintComposer = moderation.closeComplaintComposer,
                    onSubmitComplaint = moderation.submitPostComplaint,
                    canComposeAdminMessage = adminPresentation.canDeleteAsAdmin,
                    adminUserCardAnchorId = adminUserCardAnchorId,
                    adminMessageRecipientName = adminMessageRecipientName,
                    adminMessageSubject = adminMessageSubject,
                    adminMessageBody = adminMessageBody,
                    isAdminMessageSending = isAdminMessageSending,
                    adminMessageError = adminMessageError,
                    isAdminMessageComposerOpen = isAdminMessageComposerOpen,
                    onToggleAdminUserCard = admin.toggleAdminUserCard,
                    onOpenAdminMessageComposer = admin.openAdminMessageCompose,
                    onAdminMessageSubjectChange = admin.setAdminMessageSubject,
                    onAdminMessageBodyChange = admin.setAdminMessageBody,
                    onCloseAdminMessageComposer = admin.closeAdminUserCard,
                    onSendAdminMessage = admin.sendAdminMessageToCurrentRecipient,
                    showBackToTopics = showBackButtons && viewport.mobilePane != MobilePane.Topics,
                    onBackToTopics = { viewport.setMobilePane(MobilePane.Topics) },
                    containerRef = postsContainerRef,
                    loadMoreRef = loadMoreRef,
                )
                repliesColumn(
                    translator = translator,
                    visualsFactory = visualsFactory,
                    selectedPost = selectedPost,
                    replies = replies,
                    isLoading = isLoadingReplies,
                    connectedGid = props.profile.connectedIdentityGid,
                    canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
                    deleteCandidateId = deleteCandidate?.id,
                    isDeleting = isDeletingPost,
                    deleteError = deleteError,
                    onRequestDelete = content.requestDelete,
                    onCancelDelete = content.clearDeleteCandidate,
                    onConfirmDelete = content.confirmDelete,
                    complaintActionPostId = complaintActionPostId,
                    complaintComposerPostId = complaintComposerPostId,
                    complaintComposerText = complaintComposerText,
                    complaintComposerError = complaintComposerError,
                    isComplaintSubmitting = isComplaintSubmitting,
                    onRequestComplaint = moderation.requestPostComplaint,
                    onComplaintTextChange = moderation.setComplaintComposerText,
                    onCloseComplaintComposer = moderation.closeComplaintComposer,
                    onSubmitComplaint = moderation.submitPostComplaint,
                    isMobile = viewport.isMobile,
                    showBackToPosts = showBackButtons,
                    onBackToPosts = {
                        if (selectedTopic == null) {
                            viewport.setMobilePane(MobilePane.Topics)
                        } else {
                            viewport.setMobilePane(MobilePane.Posts)
                        }
                    },
                    containerRef = repliesContainerRef,
                    loadMoreRef = repliesLoadMoreRef,
                    isComposerOpen = isReplyComposerOpen,
                    replyText = replyComposerText,
                    canActivateComposer = selectedPost != null && !isReplyPublishing,
                    isPublishing = isReplyPublishing,
                    errorText = replyPublishError,
                    onOpenComposer = content.openReplyComposer,
                    onCloseComposer = content.closeReplyComposer,
                    onReplyTextChange = content.setReplyComposerText,
                    onSendReply = content.sendReply,
                    canComposeAdminMessage = adminPresentation.canDeleteAsAdmin,
                    adminUserCardAnchorId = adminUserCardAnchorId,
                    adminMessageRecipientName = adminMessageRecipientName,
                    adminMessageSubject = adminMessageSubject,
                    adminMessageBody = adminMessageBody,
                    isAdminMessageSending = isAdminMessageSending,
                    adminMessageError = adminMessageError,
                    isAdminMessageComposerOpen = isAdminMessageComposerOpen,
                    onToggleAdminUserCard = admin.toggleAdminUserCard,
                    onOpenAdminMessageComposer = admin.openAdminMessageCompose,
                    onAdminMessageSubjectChange = admin.setAdminMessageSubject,
                    onAdminMessageBodyChange = admin.setAdminMessageBody,
                    onCloseAdminMessageComposer = admin.closeAdminUserCard,
                    onSendAdminMessage = admin.sendAdminMessageToCurrentRecipient,
                )
            }
        }
        if (!viewport.isMobile || viewport.mobilePane != MobilePane.Replies) {
            floatingComposerButton(
                label = translator.translate("connected.composer.open"),
                onActivate = content.openComposer,
            )
        }
    }

    val shouldRenderProfile = isProfileVisible || profileExitAnimation != ProfileExitAnimation.None

    if (shouldRenderProfile) {
        IdentityProfileView {
            applyFrom(props.profile.copy(isAdmin = adminPresentation.showsDisplayedAdminBadge))
            val connectProfile = props.profile.onConnect
            onClose = { hideProfile() }
            onConnect = {
                connectProfile()
                hideProfileWithSuction()
            }
            canDeletePosts = props.profile.canDeletePosts && !isDeletingAllPosts
            onDeletePosts = content.deleteAllPosts
            exitAnimation = profileExitAnimation
            entryAnimation = profileEntryAnimation
        }
    }

    if (isComposerOpen) {
        composerDialog(
            translator = translator,
            text = composerText,
            onTextChange = content.setComposerText,
            onClose = content.closeComposer,
            onSend = content.sendPost,
            isPublishing = isPublishing,
            errorText = publishError,
        )
    }

    toastMessage?.let { message ->
        div {
            className = ClassName("connected-toast")
            +message
        }
    }

    if (adminUserCardAnchorId != null && adminMessageRecipientName != null && adminMessageRecipientAvatar != null) {
        adminUserActionCardOverlay(
            translator = translator,
            identityVisualsFactory = visualsFactory,
            recipientAvatar = adminMessageRecipientAvatar,
            recipientName = adminMessageRecipientName,
            isComposerOpen = isAdminMessageComposerOpen,
            subject = adminMessageSubject,
            body = adminMessageBody,
            isSending = isAdminMessageSending,
            errorText = adminMessageError,
            historyEntries = adminHistoryEntries,
            isHistoryLoading = isAdminHistoryLoading,
            isHistoryAppending = isAdminHistoryAppending,
            historyErrorText = adminHistoryError,
            historyHasMore = adminHistoryHasMore,
            historyListRef = adminHistoryListRef,
            onOpenComposer = admin.openAdminMessageCompose,
            onSubjectChange = admin.setAdminMessageSubject,
            onBodyChange = admin.setAdminMessageBody,
            onClose = admin.closeAdminUserCard,
            onSend = admin.sendAdminMessageToCurrentRecipient,
            onHistoryScroll = admin.loadMoreAdminHistory,
        )
    }
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

internal fun isScrolledNearBottom(element: HTMLDivElement): Boolean {
    val thresholdPx = 24
    return element.scrollTop + element.clientHeight >= element.scrollHeight - thresholdPx
}

internal fun closestAttribute(element: Element?, attribute: String): String? {
    var current = element
    while (current != null) {
        val value = current.getAttribute(attribute)
        if (value != null) return value
        current = current.parentElement
    }
    return null
}

internal fun formatTimestamp(epochSeconds: Long): String =
    Date(epochSeconds.toDouble() * 1000).toLocaleString()

internal fun formatSimilarity(similarity: Float): String =
    (similarity.toDouble().asDynamic().toFixed(3) as String)
