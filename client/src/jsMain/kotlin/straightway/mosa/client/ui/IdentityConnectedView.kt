@file:OptIn(ExperimentalWasmJsInterop::class)

package straightway.mosa.client.ui

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.js.Date
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsName
import kotlin.js.unsafeCast
import kotlin.math.abs
import org.w3c.dom.events.Event
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
import react.dom.events.TouchEvent
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
import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.QueryResult
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult
import web.cssom.ClassName
import web.html.HTMLButtonElement
import web.html.HTMLTextAreaElement
import web.html.HTMLInputElement

external interface IdentityConnectedViewProps : Props {
    var identityName: String
    var profile: IdentityScreen.Profile
}

private external interface JsIntersectionObserverEntry {
    val isIntersecting: Boolean
}

private external interface JsIntersectionObserverOptions {
    var root: Element?
    var rootMargin: String?
    var threshold: dynamic
}

@JsName("IntersectionObserver")
private external class JsIntersectionObserver(
    callback: (Array<JsIntersectionObserverEntry>, JsIntersectionObserver) -> Unit,
    options: JsIntersectionObserverOptions = definedExternally,
) {
    fun observe(target: Element)
    fun disconnect()
}

private enum class MobilePane {
    Topics,
    Posts,
    Replies,
}

private data class SwipeTargets(
    var topic: Topic?,
    var post: QueryResult.Entry?,
)

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
    val (adminUserCardAnchorId, setAdminUserCardAnchorId) = useState<String?>(null)
    val (adminMessageRecipientGid, setAdminMessageRecipientGid) = useState<String?>(null)
    val (adminMessageRecipientName, setAdminMessageRecipientName) = useState<String?>(null)
    val (adminMessageRecipientAvatar, setAdminMessageRecipientAvatar) = useState<String?>(null)
    val (adminMessageSubject, setAdminMessageSubject) = useState("")
    val (adminMessageBody, setAdminMessageBody) = useState("")
    val (isAdminMessageSending, setAdminMessageSending) = useState(false)
    val (adminMessageError, setAdminMessageError) = useState<String?>(null)
    val (isAdminMessageComposerOpen, setAdminMessageComposerOpen) = useState(false)
    val (adminHistoryEntries, setAdminHistoryEntries) = useState<List<AdminMessage>>(emptyList())
    val (isAdminHistoryLoading, setAdminHistoryLoading) = useState(false)
    val (isAdminHistoryAppending, setAdminHistoryAppending) = useState(false)
    val (adminHistoryError, setAdminHistoryError) = useState<String?>(null)
    val (adminHistoryPage, setAdminHistoryPage) = useState(1)
    val (adminHistoryHasMore, setAdminHistoryHasMore) = useState(false)
    val (isMobile, setIsMobile) = useState(false)
    val (isTouchDevice, setIsTouchDevice) = useState(false)
    val (mobilePane, setMobilePane) = useState(MobilePane.Topics)
    val touchStartXRef = useRef(0.0)
    val touchStartYRef = useRef(0.0)
    val swipePaneRef = useRef(MobilePane.Topics)
    val swipeActiveRef = useRef(false)
    val swipeTargetsRef = useRef(SwipeTargets(null, null))
    val (swipeOffsetPercent, setSwipeOffsetPercent) = useState(0.0)
    val adminUserCardAnchorIdRef = useRef("")
    val adminHistoryListRef = useRef<HTMLDivElement>()
    val adminHistoryScrollRestoreRef = useRef<Double>()
    adminUserCardAnchorIdRef.current = adminUserCardAnchorId ?: ""

    fun setPaneForSelectedTopic(topic: Topic?) {
        if (!isMobile) return
        setMobilePane(if (topic == null) MobilePane.Topics else MobilePane.Posts)
    }

    fun setPaneForSelectedPost(entry: QueryResult.Entry?) {
        if (!isMobile) return
        setMobilePane(if (entry == null) MobilePane.Posts else MobilePane.Replies)
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
            isMobile = isMobile,
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

    fun closeAdminUserCard() {
        if (isAdminMessageSending) return
        setAdminUserCardAnchorId(null)
        setAdminMessageRecipientGid(null)
        setAdminMessageRecipientName(null)
        setAdminMessageRecipientAvatar(null)
        setAdminMessageSubject("")
        setAdminMessageBody("")
        setAdminMessageError(null)
        setAdminMessageComposerOpen(false)
        setAdminHistoryEntries(emptyList())
        setAdminHistoryLoading(false)
        setAdminHistoryAppending(false)
        setAdminHistoryError(null)
        setAdminHistoryPage(1)
        setAdminHistoryHasMore(false)
    }

    fun toggleAdminUserCard(
        anchorId: String,
        recipientGid: String,
        recipientName: String,
        recipientAvatar: String,
    ) {
        if (!adminPresentation.canDeleteAsAdmin) return
        if (adminUserCardAnchorId == anchorId) {
            closeAdminUserCard()
            return
        }
        setAdminUserCardAnchorId(anchorId)
        setAdminMessageRecipientGid(recipientGid)
        setAdminMessageRecipientName(recipientName)
        setAdminMessageRecipientAvatar(recipientAvatar)
        setAdminMessageSubject("")
        setAdminMessageBody("")
        setAdminMessageError(null)
        setAdminMessageComposerOpen(false)
        setAdminHistoryLoading(true)
        setAdminHistoryAppending(false)
        setAdminHistoryError(null)
        scope.launch {
            val result: AdminMessageListResult? =
                runCatching { adminMessageHistoryService.fetch(recipientGid, page = 1) }.getOrNull()
            setAdminHistoryLoading(false)
            if (result == null) {
                setAdminHistoryEntries(emptyList())
                setAdminHistoryPage(1)
                setAdminHistoryHasMore(false)
                setAdminHistoryError(translator.translate("connected.messages.actions.history.error"))
            } else {
                setAdminHistoryEntries(result.entries)
                setAdminHistoryPage(result.page)
                setAdminHistoryHasMore(result.hasMore)
            }
        }
    }

    fun openAdminMessageCompose() {
        if (adminUserCardAnchorId == null || adminMessageRecipientGid == null) return
        setAdminMessageError(null)
        setAdminMessageComposerOpen(true)
    }

    fun refreshAdminHistory(recipientGid: String) {
        setAdminHistoryLoading(true)
        setAdminHistoryAppending(false)
        setAdminHistoryError(null)
        scope.launch {
            val result: AdminMessageListResult? =
                runCatching { adminMessageHistoryService.fetch(recipientGid, page = 1) }.getOrNull()
            setAdminHistoryLoading(false)
            if (result == null) {
                setAdminHistoryEntries(emptyList())
                setAdminHistoryPage(1)
                setAdminHistoryHasMore(false)
                setAdminHistoryError(translator.translate("connected.messages.actions.history.error"))
            } else {
                setAdminHistoryEntries(result.entries)
                setAdminHistoryPage(result.page)
                setAdminHistoryHasMore(result.hasMore)
            }
        }
    }

    fun loadMoreAdminHistory() {
        val recipientGid = adminMessageRecipientGid ?: return
        if (isAdminHistoryLoading || isAdminHistoryAppending || !adminHistoryHasMore) return
        adminHistoryScrollRestoreRef.current = adminHistoryListRef.current?.scrollTop
        setAdminHistoryAppending(true)
        setAdminHistoryError(null)
        val nextPage = adminHistoryPage + 1
        scope.launch {
            val result: AdminMessageListResult? =
                runCatching { adminMessageHistoryService.fetch(recipientGid, page = nextPage) }.getOrNull()
            setAdminHistoryAppending(false)
            if (result == null) {
                setAdminHistoryError(translator.translate("connected.messages.actions.history.error"))
            } else {
                setAdminHistoryEntries((adminHistoryEntries + result.entries).distinctBy(AdminMessage::id))
                setAdminHistoryPage(result.page)
                setAdminHistoryHasMore(result.hasMore)
            }
        }
    }

    fun sendAdminMessageToCurrentRecipient() {
        val recipientGid = adminMessageRecipientGid ?: return
        scope.launch {
            setAdminMessageSending(true)
            setAdminMessageError(null)
            val wasSent =
                runCatching {
                    adminMessagePublisher.send(
                        recipientGid,
                        adminMessageSubject,
                        adminMessageBody,
                    )
                }.getOrDefault(false)
            setAdminMessageSending(false)
            if (wasSent) {
                setAdminMessageSubject("")
                setAdminMessageBody("")
                setAdminMessageError(null)
                setAdminMessageComposerOpen(false)
                refreshAdminHistory(recipientGid)
                showToast(translator.translate("connected.messages.compose.success"))
            } else {
                setAdminMessageError(translator.translate("connected.messages.compose.error"))
            }
        }
    }

    fun closestAttribute(element: Element?, attribute: String): String? {
        var current = element
        while (current != null) {
            val value = current.getAttribute(attribute)
            if (value != null) return value
            current = current.parentElement
        }
        return null
    }

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
        val adminUserCardOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if ((adminUserCardAnchorIdRef.current ?: "").isEmpty()) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedTrigger = target.asDynamic().closest(".admin-user-trigger") != null
            val clickedCard = target.asDynamic().closest(".admin-user-card") != null
            if (!clickedTrigger && !clickedCard) {
                closeAdminUserCard()
            }
        }
        document.addEventListener("mousedown", adminUserCardOutsideClickListener)
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("mousedown", adminUserCardOutsideClickListener)
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

    useEffect(arrayOf(posts, selectedTopic, isMobile, mobilePane)) {
        val target = loadMoreRef.current ?: return@useEffect
        val container = postsContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        if (isMobile && mobilePane != MobilePane.Posts) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        if (isMobile) {
            options.root = null
            options.rootMargin = "120px"
        } else {
            options.root = container
            options.rootMargin = "48px"
        }
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) {
                        content.loadMorePosts()
                    }
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    useEffect(arrayOf(replies, selectedPost, isMobile, mobilePane)) {
        val target = repliesLoadMoreRef.current ?: return@useEffect
        val container = repliesContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        if (isMobile && mobilePane != MobilePane.Replies) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        if (isMobile) {
            options.root = null
            options.rootMargin = "120px"
        } else {
            options.root = container
            options.rootMargin = "48px"
        }
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) {
                        content.loadMoreReplies()
                    }
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    useEffect(arrayOf(selectedTopic, selectedPost, mobilePane)) {
        fun updateIsMobile() {
            val isNarrow = window.innerWidth <= 900
            setIsMobile(isNarrow)
            if (!isNarrow) {
                setMobilePane(MobilePane.Topics)
                return
            }
            if (mobilePane == MobilePane.Replies && selectedPost == null) {
                if (selectedTopic == null) {
                    setMobilePane(MobilePane.Topics)
                } else {
                    setMobilePane(MobilePane.Posts)
                }
            }
            if (mobilePane == MobilePane.Posts && selectedTopic == null) {
                setMobilePane(MobilePane.Topics)
            }
        }
        updateIsMobile()
        val listener: (Event) -> Unit = { updateIsMobile() }
        window.addEventListener("resize", listener)
        try {
            awaitCancellation()
        } finally {
            window.removeEventListener("resize", listener)
        }
    }

    useEffect(arrayOf(adminHistoryEntries, isAdminHistoryAppending)) {
        val scrollTop = adminHistoryScrollRestoreRef.current ?: return@useEffect
        adminHistoryListRef.current?.scrollTop = scrollTop
        if (!isAdminHistoryAppending) {
            adminHistoryScrollRestoreRef.current = null
        }
    }

    useEffect(*noDependencies) {
        val coarsePointer = window.matchMedia("(pointer: coarse)").matches
        val hasTouch = js("('ontouchstart' in window)") as Boolean
        setIsTouchDevice(coarsePointer || hasTouch)
    }

    useEffect(arrayOf(isMobile, selectedTopic, selectedPost, mobilePane)) {
        if (!isMobile) return@useEffect
        if (selectedTopic == null && mobilePane != MobilePane.Topics) {
            setMobilePane(MobilePane.Topics)
            return@useEffect
        }
        if (selectedPost == null && mobilePane == MobilePane.Replies) {
            setMobilePane(MobilePane.Posts)
        }
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
                buildString {
                    append("topic-board")
                    if (isMobile) {
                        append(" topic-board--mobile")
                        append(
                            when (mobilePane) {
                                MobilePane.Topics -> " topic-board--mobile-topics"
                                MobilePane.Posts -> " topic-board--mobile-posts"
                                MobilePane.Replies -> " topic-board--mobile-replies"
                            },
                        )
                        if (swipeOffsetPercent != 0.0) {
                            append(" topic-board--mobile-dragging")
                        }
                    }
                }
            className = ClassName(boardClass)
            val baseShiftPercent =
                when (mobilePane) {
                    MobilePane.Topics -> 0.0
                    MobilePane.Posts -> -100.0
                    MobilePane.Replies -> -200.0
                }
            val dragShiftPercent = if (isMobile) swipeOffsetPercent else 0.0
            val combinedShiftPercent = baseShiftPercent + dragShiftPercent
            val showBackButtons = isMobile && !isTouchDevice
            div {
                className = ClassName("topic-board__track")
                if (isMobile) {
                    val trackStyle: dynamic = js("({})")
                    trackStyle["--mobile-shift"] = "${combinedShiftPercent}%"
                    asDynamic().style = trackStyle
                }
                if (isMobile) {
                    val handleStart: (TouchEvent<*>) -> Unit = handler@ { event ->
                        val touch = event.changedTouches.item(0) ?: return@handler
                        touchStartXRef.current = touch.clientX
                        touchStartYRef.current = touch.clientY
                        swipePaneRef.current = mobilePane
                        val targets = swipeTargetsRef.current ?: return@handler
                        val eventTarget = event.target as? Element
                        val topicTitle = closestAttribute(eventTarget, "data-topic-title")
                        val postId = closestAttribute(eventTarget, "data-post-id")
                        targets.topic = content.findTopicByTitle(topicTitle)
                        targets.post = content.findPostById(postId)
                        swipeActiveRef.current = true
                        setSwipeOffsetPercent(0.0)
                    }
                    val handleMove: (TouchEvent<*>) -> Unit = handler@ { event ->
                        if (swipeActiveRef.current != true) return@handler
                        val targets = swipeTargetsRef.current ?: return@handler
                        val touch = event.changedTouches.item(0) ?: return@handler
                        val startX = touchStartXRef.current ?: return@handler
                        val startY = touchStartYRef.current ?: return@handler
                        val touchX = touch.clientX
                        val touchY = touch.clientY
                        val deltaX = touchX - startX
                        val deltaY = abs(touchY - startY)
                        val absDeltaX = abs(deltaX)
                        if (absDeltaX <= deltaY) {
                            setSwipeOffsetPercent(0.0)
                            return@handler
                        }
                        val activePane = swipePaneRef.current ?: mobilePane
                        val trackWidth =
                            (event.currentTarget as? HTMLDivElement)?.clientWidth?.toDouble()
                                ?.coerceAtLeast(1.0)
                                ?: window.innerWidth.toDouble().coerceAtLeast(1.0)
                        val percent = (deltaX / trackWidth) * 100.0
                        when {
                            deltaX > 0 -> {
                                if (activePane == MobilePane.Topics) {
                                    setSwipeOffsetPercent(0.0)
                                    return@handler
                                }
                                setSwipeOffsetPercent(percent.coerceIn(0.0, 100.0))
                            }
                            deltaX < 0 -> {
                                when (activePane) {
                                    MobilePane.Topics -> {
                                        if (targets.topic == null) {
                                            setSwipeOffsetPercent(0.0)
                                            return@handler
                                        }
                                        setSwipeOffsetPercent(percent.coerceIn(-100.0, 0.0))
                                    }
                                    MobilePane.Posts -> {
                                        if (targets.post == null) {
                                            setSwipeOffsetPercent(0.0)
                                            return@handler
                                        }
                                        setSwipeOffsetPercent(percent.coerceIn(-100.0, 0.0))
                                    }
                                    MobilePane.Replies -> {
                                        setSwipeOffsetPercent(0.0)
                                        return@handler
                                    }
                                }
                            }
                        }
                    }
                    val handleEnd: (TouchEvent<*>) -> Unit = handler@ {
                        if (swipeActiveRef.current != true) return@handler
                        val targets = swipeTargetsRef.current ?: return@handler
                        val activePane = swipePaneRef.current ?: mobilePane
                        val offset = swipeOffsetPercent
                        val backThresholdReached = offset > 18.0
                        val forwardThresholdReached = offset < -18.0
                        if (backThresholdReached) {
                            when (activePane) {
                                MobilePane.Replies -> setMobilePane(MobilePane.Posts)
                                MobilePane.Posts -> setMobilePane(MobilePane.Topics)
                                MobilePane.Topics -> Unit
                            }
                        }
                        if (forwardThresholdReached) {
                            when (activePane) {
                                MobilePane.Topics -> targets.topic?.let { target ->
                                    content.selectTopic(target)
                                    setMobilePane(MobilePane.Posts)
                                }
                                MobilePane.Posts -> targets.post?.let { post ->
                                    content.selectPost(post)
                                }
                                MobilePane.Replies -> Unit
                            }
                        }
                        setSwipeOffsetPercent(0.0)
                        swipeActiveRef.current = false
                        targets.topic = null
                        targets.post = null
                    }
                    onTouchStart = handleStart
                    onTouchMove = handleMove
                    onTouchEnd = handleEnd
                    onTouchCancel = handleEnd
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
                    onToggleAdminUserCard = ::toggleAdminUserCard,
                    onOpenAdminMessageComposer = ::openAdminMessageCompose,
                    onAdminMessageSubjectChange = { value -> setAdminMessageSubject(value) },
                    onAdminMessageBodyChange = { value -> setAdminMessageBody(value) },
                    onCloseAdminMessageComposer = ::closeAdminUserCard,
                    onSendAdminMessage = ::sendAdminMessageToCurrentRecipient,
                    showBackToTopics = showBackButtons && mobilePane != MobilePane.Topics,
                    onBackToTopics = { setMobilePane(MobilePane.Topics) },
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
                    isMobile = isMobile,
                    showBackToPosts = showBackButtons,
                    onBackToPosts = {
                        if (selectedTopic == null) {
                            setMobilePane(MobilePane.Topics)
                        } else {
                            setMobilePane(MobilePane.Posts)
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
                    onToggleAdminUserCard = ::toggleAdminUserCard,
                    onOpenAdminMessageComposer = ::openAdminMessageCompose,
                    onAdminMessageSubjectChange = { value -> setAdminMessageSubject(value) },
                    onAdminMessageBodyChange = { value -> setAdminMessageBody(value) },
                    onCloseAdminMessageComposer = ::closeAdminUserCard,
                    onSendAdminMessage = ::sendAdminMessageToCurrentRecipient,
                )
            }
        }
        if (!isMobile || mobilePane != MobilePane.Replies) {
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
            onOpenComposer = ::openAdminMessageCompose,
            onSubjectChange = { value -> setAdminMessageSubject(value) },
            onBodyChange = { value -> setAdminMessageBody(value) },
            onClose = ::closeAdminUserCard,
            onSend = ::sendAdminMessageToCurrentRecipient,
            onHistoryScroll = ::loadMoreAdminHistory,
        )
    }
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

internal fun isScrolledNearBottom(element: HTMLDivElement): Boolean {
    val thresholdPx = 24
    return element.scrollTop + element.clientHeight >= element.scrollHeight - thresholdPx
}

internal fun formatTimestamp(epochSeconds: Long): String =
    Date(epochSeconds.toDouble() * 1000).toLocaleString()

internal fun formatSimilarity(similarity: Float): String =
    (similarity.toDouble().asDynamic().toFixed(3) as String)
