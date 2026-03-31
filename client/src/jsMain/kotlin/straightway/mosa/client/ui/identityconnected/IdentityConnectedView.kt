package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.content.*
import straightway.mosa.client.ui.identityconnected.moderation.*
import straightway.mosa.client.ui.identityconnected.shell.*

import straightway.mosa.client.ui.*

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import react.FC
import react.Props
import react.useEffect
import react.useMemo
import react.useRef
import react.useState
import web.html.HTMLDivElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.client.content.AllPostsDeletionService
import straightway.mosa.client.content.ContentCapabilitiesService
import straightway.mosa.client.content.PostCache
import straightway.mosa.client.content.PostDeletionService
import straightway.mosa.client.content.PostPublisher
import straightway.mosa.client.content.ReplyCache
import straightway.mosa.client.content.ReplyPublisher
import straightway.mosa.client.content.ReplySearchService
import straightway.mosa.client.content.TopicRepository
import straightway.mosa.client.content.TopicSearchService
import straightway.mosa.client.content.complaint.AdminPostComplaintService
import straightway.mosa.client.content.complaint.PostComplaintService
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.identity.FictionalNameGenerator

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
    val visualsFactory = useMemo(nameGenerator, avatarGenerator) { IdentityVisualsFactory(nameGenerator, avatarGenerator) }
    val scope = useMemo(*noDependencies) { MainScope() }
    val postsContainerRef = useRef<HTMLDivElement>()
    val loadMoreRef = useRef<HTMLDivElement>()
    val repliesContainerRef = useRef<HTMLDivElement>()
    val repliesLoadMoreRef = useRef<HTMLDivElement>()
    val contentRefs =
        ConnectedViewContentRefs(
            postsContainerRef = postsContainerRef,
            loadMoreRef = loadMoreRef,
            repliesContainerRef = repliesContainerRef,
            repliesLoadMoreRef = repliesLoadMoreRef,
        )
    val asyncContext = ConnectedViewAsyncContext(translator = translator, scope = scope)
    val viewport = useViewportController()
    var pendingProfileConnectGid by useState<String?>(null)

    val profileController =
        useProfileController(
            sessionStore = sessionStore,
            scope = scope,
            isPersistedProfile = props.profile.isPersisted,
            persistProfileVisibilitySnapshot = { visible -> persistProfileVisibilitySnapshot(sessionStore, visible) },
        )
    val feedbackContext = ConnectedViewFeedbackContext(async = asyncContext, showToast = profileController.actions.showToast)
    val adminPresentation =
        useIdentityAdminPresentation(
            connectedIdentityGid = props.profile.connectedIdentityGid,
            displayedIdentityGid = props.profile.identityGid,
            isProfileVisible = profileController.state.isProfileVisible,
            contentCapabilitiesService = contentCapabilitiesService,
            scope = scope,
        )
    val content =
        useContentController(
            dependencies =
                ContentControllerDependencies(
                    context = feedbackContext,
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
                    isMobile = viewport.state.isMobile,
                    snapshotRefreshVersion = props.profile.snapshotRefreshVersion,
                    refs = contentRefs,
                ),
            callbacks =
                ContentControllerCallbacks(
                    persistSessionSnapshot = { builder -> persistSessionSnapshot(sessionStore, builder) },
                    onTopicPaneRequested = { topic ->
                        setPaneForSelectedTopic(viewport.state.isMobile, viewport.actions.setMobilePane, topic)
                    },
                    onPostPaneRequested = { entry ->
                        setPaneForSelectedPost(viewport.state.isMobile, viewport.actions.setMobilePane, entry)
                    },
                ),
        )
    val moderation =
        useModerationController(
            ModerationControllerDependencies(
                context = feedbackContext,
                inboxStateStore = inboxStateStore,
                adminMessageInboxService = adminMessageInboxService,
                postComplaintService = postComplaintService,
                adminPostComplaintService = adminPostComplaintService,
                podBaseProvider = podBaseProvider,
                connectedIdentityGid = props.profile.connectedIdentityGid,
                showsConnectedAdminBadge = adminPresentation.showsConnectedAdminBadge,
            ),
        )
    val admin =
        useAdminController(
            AdminActionDependencies(
                context = feedbackContext,
                adminMessageHistoryService = adminMessageHistoryService,
                adminMessagePublisher = adminMessagePublisher,
                canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
            ),
        )
    val shell =
        useMobileShell(
            state =
                MobileShellState(
                    isMobile = viewport.state.isMobile,
                    isTouchDevice = viewport.state.isTouchDevice,
                    mobilePane = viewport.state.mobilePane,
                    selectedTopic = content.state.selectedTopic,
                    selectedPost = content.state.selectedPost,
                    posts = content.state.posts,
                    replies = content.state.replies,
                ),
            refs =
                MobileShellRefs(
                    contentRefs = contentRefs,
                ),
            actions =
                MobileShellActions(
                    setMobilePane = viewport.actions.setMobilePane,
                    findTopicByTitle = content.lookups.findTopicByTitle,
                    findPostById = content.lookups.findPostById,
                    selectTopic = content.actions.selectTopic,
                    selectPost = content.actions.selectPost,
                    loadMorePosts = content.actions.loadMorePosts,
                    loadMoreReplies = content.actions.loadMoreReplies,
                ),
        )
    val uiState =
        buildShellUiState(
            ShellUiStateInputs(
                translator = asyncContext.translator,
                avatarTitleKey = adminPresentation.avatarTitleKey,
                isMobile = viewport.state.isMobile,
                mobilePane = viewport.state.mobilePane,
                isProfileVisible = profileController.state.isProfileVisible,
                profileExitAnimation = profileController.state.profileExitAnimation,
            ),
        )
    useEffect(
        profileController.state.isProfileVisible,
        pendingProfileConnectGid,
        props.profile.identityGid,
        props.profile.connectedIdentityGid,
    ) {
        val pendingGid = pendingProfileConnectGid
        if (!profileController.state.isProfileVisible || pendingGid == null) return@useEffect
        val displayedGid = props.profile.identityGid
        val connectedGid = props.profile.connectedIdentityGid
        if (pendingGid == displayedGid && pendingGid == connectedGid) {
            pendingProfileConnectGid = null
            profileController.actions.hideProfileWithSuction()
        }
    }
    useEffect(*noDependencies) {
        try {
            awaitCancellation()
        } finally {
            scope.cancel()
        }
    }

    val headerProps =
        HeaderProps(
            identityName = props.identityName,
            profile = props.profile,
            visualsFactory = visualsFactory,
            adminPresentation = adminPresentation,
            profileController = profileController,
            moderation = moderation,
            uiState = uiState,
        )
    val boardProps =
        BoardProps(
            profile = props.profile,
            visualsFactory = visualsFactory,
            adminPresentation = adminPresentation,
            viewport = viewport,
            shell = shell,
            content = content,
            moderation = moderation,
            admin = admin,
            uiState = uiState,
            postsContainerRef = postsContainerRef,
            loadMoreRef = loadMoreRef,
            repliesContainerRef = repliesContainerRef,
            repliesLoadMoreRef = repliesLoadMoreRef,
        )
    val overlayProps =
        OverlayProps(
            profile = props.profile,
            visualsFactory = visualsFactory,
            adminPresentation = adminPresentation,
            profileController = profileController,
            onProfileConnectRequested = { pendingProfileConnectGid = it },
            content = content,
            admin = admin,
            uiState = uiState,
        )

    connectedShellLayout(
        translator = translator,
        headerProps = headerProps,
        boardProps = boardProps,
    )
    connectedShellOverlays(translator, overlayProps)
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()
