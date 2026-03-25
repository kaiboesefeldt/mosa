package straightway.mosa.client.ui

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import react.FC
import react.Props
import react.useEffect
import react.useMemo
import react.useRef
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
    val viewport = useIdentityConnectedViewViewportController()

    val profileController =
        useIdentityConnectedViewProfileController(
            sessionStore = sessionStore,
            scope = scope,
            isPersistedProfile = props.profile.isPersisted,
            persistProfileVisibilitySnapshot = { visible -> persistProfileVisibilitySnapshot(sessionStore, visible) },
        )
    val adminPresentation =
        useIdentityAdminPresentation(
            connectedIdentityGid = props.profile.connectedIdentityGid,
            displayedIdentityGid = props.profile.identityGid,
            isProfileVisible = profileController.isProfileVisible,
            contentCapabilitiesService = contentCapabilitiesService,
            scope = scope,
        )
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
            showToast = profileController.showToast,
            persistSessionSnapshot = { builder -> persistSessionSnapshot(sessionStore, builder) },
            onTopicPaneRequested = { topic ->
                setPaneForSelectedTopic(viewport.isMobile, viewport.setMobilePane, topic)
            },
            onPostPaneRequested = { entry ->
                setPaneForSelectedPost(viewport.isMobile, viewport.setMobilePane, entry)
            },
        )
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
            showToast = profileController.showToast,
        )
    val admin =
        useIdentityConnectedViewAdminController(
            translator = translator,
            adminMessageHistoryService = adminMessageHistoryService,
            adminMessagePublisher = adminMessagePublisher,
            scope = scope,
            canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
            showToast = profileController.showToast,
        )
    val shell =
        useIdentityConnectedViewMobileShell(
            isMobile = viewport.isMobile,
            isTouchDevice = viewport.isTouchDevice,
            mobilePane = viewport.mobilePane,
            setMobilePane = viewport.setMobilePane,
            selectedTopic = content.selectedTopic,
            selectedPost = content.selectedPost,
            posts = content.posts,
            replies = content.replies,
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
    val uiState = buildIdentityConnectedViewUiState(
        translator = translator,
        avatarTitleKey = adminPresentation.avatarTitleKey,
        isMobile = viewport.isMobile,
        mobilePane = viewport.mobilePane,
        isProfileVisible = profileController.isProfileVisible,
        profileExitAnimation = profileController.profileExitAnimation,
    )
    useEffect(*noDependencies) {
        try {
            awaitCancellation()
        } finally {
            scope.cancel()
        }
    }

    val headerProps =
        IdentityConnectedViewHeaderProps(
            identityName = props.identityName,
            profile = props.profile,
            visualsFactory = visualsFactory,
            adminPresentation = adminPresentation,
            profileController = profileController,
            moderation = moderation,
            uiState = uiState,
        )
    val boardProps =
        IdentityConnectedViewBoardProps(
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
        IdentityConnectedViewOverlayProps(
            profile = props.profile,
            visualsFactory = visualsFactory,
            adminPresentation = adminPresentation,
            profileController = profileController,
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
