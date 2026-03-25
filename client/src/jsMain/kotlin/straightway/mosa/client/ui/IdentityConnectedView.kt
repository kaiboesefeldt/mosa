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
import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.complaint.PostComplaintAvailability
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

private val emptyInboxStateSnapshot =
    AdminMessageInboxStateSnapshot(
        knownMessageIds = emptyList(),
        seenMessageIds = emptyList(),
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
    val (topics, setTopics) = useState<List<Topic>>(emptyList())
    val (selectedTopic, setSelectedTopic) = useState<Topic?>(null)
    val (posts, setPosts) = useState<List<TopicPosts>>(emptyList())
    val (selectedPost, setSelectedPost) = useState<QueryResult.Entry?>(null)
    val (replies, setReplies) = useState<ThreadReplies?>(null)
    val (isLoadingReplies, setLoadingReplies) = useState(false)
    val (topicFilters, setTopicFilters) = useState<Map<String, Float>>(emptyMap())
    val (isLoadingPosts, setLoadingPosts) = useState(false)
    val postsContainerRef = useRef<HTMLDivElement>()
    val loadMoreRef = useRef<HTMLDivElement>()
    val isAppendingPostsRef = useRef(false)
    val postsRequestVersion = useRef(0)
    val repliesContainerRef = useRef<HTMLDivElement>()
    val repliesLoadMoreRef = useRef<HTMLDivElement>()
    val isAppendingRepliesRef = useRef(false)
    val repliesRequestVersion = useRef(0)
    val (isProfileVisible, setProfileVisible) = useState(false)
    val (profileEntryAnimation, setProfileEntryAnimation) = useState(ProfileEntryAnimation.None)
    val profileEntryTimerRef = useRef<Int>()
    val (profileExitAnimation, setProfileExitAnimation) = useState(ProfileExitAnimation.None)
    val profileExitTimerRef = useRef<Int>()
    val profileAnimationDurationMs = 760
    val (isComposerOpen, setComposerOpen) = useState(false)
    val (composerText, setComposerText) = useState("")
    val (isPublishing, setPublishing) = useState(false)
    val (publishError, setPublishError) = useState<String?>(null)
    val (toastMessage, setToastMessage) = useState<String?>(null)
    val toastTimerRef = useRef<Int>()
    val (isInboxOpen, setInboxOpen) = useState(false)
    val (isInboxLoading, setInboxLoading) = useState(false)
    val (isInboxAppending, setInboxAppending) = useState(false)
    val (inboxEntries, setInboxEntries) = useState<List<AdminMessage>>(emptyList())
    val (seenInboxMessageIds, setSeenInboxMessageIds) = useState<List<String>>(emptyList())
    val (showSeenInboxEntries, setShowSeenInboxEntries) = useState(false)
    val (hiddenSeenInboxMessageIds, setHiddenSeenInboxMessageIds) = useState<List<String>>(emptyList())
    val (inboxError, setInboxError) = useState<String?>(null)
    val (inboxPage, setInboxPage) = useState(1)
    val (inboxHasMore, setInboxHasMore) = useState(false)
    val (inboxRefreshFromTime, setInboxRefreshFromTime) = useState(0L)
    val (unreadInboxCount, setUnreadInboxCount) = useState(0)
    val (isComplaintQueueOpen, setComplaintQueueOpen) = useState(false)
    val (isComplaintQueueLoading, setComplaintQueueLoading) = useState(false)
    val (isComplaintQueueAppending, setComplaintQueueAppending) = useState(false)
    val (complaintQueueEntries, setComplaintQueueEntries) = useState<List<ComplaintQueueEntry>>(emptyList())
    val (complaintQueueError, setComplaintQueueError) = useState<String?>(null)
    val (complaintQueuePage, setComplaintQueuePage) = useState(1)
    val (complaintQueueHasMore, setComplaintQueueHasMore) = useState(false)
    val (openComplaintCount, setOpenComplaintCount) = useState(0)
    val (showProcessedComplaintEntries, setShowProcessedComplaintEntries) = useState(false)
    val (complaintActionPostId, setComplaintActionPostId) = useState<String?>(null)
    val (complaintComposerPostId, setComplaintComposerPostId) = useState<String?>(null)
    val (complaintComposerText, setComplaintComposerText) = useState("")
    val (complaintComposerError, setComplaintComposerError) = useState<String?>(null)
    val (isComplaintSubmitting, setComplaintSubmitting) = useState(false)
    val (complaintDecisionEntryId, setComplaintDecisionEntryId) = useState<String?>(null)
    val (complaintDecisionTargetStatus, setComplaintDecisionTargetStatus) = useState<PostComplaintState?>(null)
    val (complaintDecisionText, setComplaintDecisionText) = useState("")
    val (complaintDecisionError, setComplaintDecisionError) = useState<String?>(null)
    val (isComplaintDecisionSubmitting, setComplaintDecisionSubmitting) = useState(false)
    val (deleteCandidate, setDeleteCandidate) = useState<QueryResult.Entry?>(null)
    val (isDeletingPost, setDeletingPost) = useState(false)
    val (isDeletingAllPosts, setDeletingAllPosts) = useState(false)
    val (deleteError, setDeleteError) = useState<String?>(null)
    val (isReplyComposerOpen, setReplyComposerOpen) = useState(false)
    val (replyComposerText, setReplyComposerText) = useState("")
    val (isReplyPublishing, setReplyPublishing) = useState(false)
    val (replyPublishError, setReplyPublishError) = useState<String?>(null)
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
    val snapshotRefreshVersionRef = useRef(props.profile.snapshotRefreshVersion)
    val connectedIdentityGidRef = useRef(props.profile.connectedIdentityGid)
    val isInboxOpenRef = useRef(false)
    val isComplaintQueueOpenRef = useRef(false)
    val adminUserCardAnchorIdRef = useRef("")
    val inboxButtonRef = useRef<HTMLButtonElement>()
    val complaintQueueButtonRef = useRef<HTMLButtonElement>()
    val inboxPopoverRef = useRef<HTMLDivElement>()
    val complaintQueuePopoverRef = useRef<HTMLDivElement>()
    val complaintQueueEntriesRef = useRef<List<ComplaintQueueEntry>>(emptyList())
    val adminHistoryListRef = useRef<HTMLDivElement>()
    val inboxScrollRestoreRef = useRef<Double>()
    val adminHistoryScrollRestoreRef = useRef<Double>()
    isInboxOpenRef.current = isInboxOpen
    isComplaintQueueOpenRef.current = isComplaintQueueOpen
    complaintQueueEntriesRef.current = complaintQueueEntries
    adminUserCardAnchorIdRef.current = adminUserCardAnchorId ?: ""

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

    fun currentInboxStateKey(): Pair<String, String>? {
        val podBase = podBaseProvider.base()?.trim()?.takeUnless(String::isEmpty) ?: return null
        val gid = props.profile.connectedIdentityGid?.trim()?.takeUnless(String::isEmpty) ?: return null
        return podBase to gid
    }

    fun loadInboxStateSnapshot(): AdminMessageInboxStateSnapshot {
        val key = currentInboxStateKey() ?: return emptyInboxStateSnapshot
        return inboxStateStore.load(key.first, key.second) ?: emptyInboxStateSnapshot
    }

    fun persistInboxStateSnapshot(snapshot: AdminMessageInboxStateSnapshot) {
        val key = currentInboxStateKey() ?: return
        inboxStateStore.store(key.first, key.second, snapshot)
    }

    fun unreadCount(snapshot: AdminMessageInboxStateSnapshot): Int {
        val seen = snapshot.seenMessageIds.toSet()
        return snapshot.knownMessageIds.count { it !in seen }
    }

    fun mergedInboxState(
        fetchedEntries: List<AdminMessage>,
        markAsSeen: Boolean,
    ): AdminMessageInboxStateSnapshot {
        val currentSnapshot = loadInboxStateSnapshot()
        return currentSnapshot.mergeFetchedEntries(
            fetchedEntries = fetchedEntries,
            markFetchedEntriesAsSeen = markAsSeen,
        )
    }

    fun applyInboxPage(
        result: AdminMessageListResult,
        markAsSeen: Boolean,
        hiddenSeenMessageIds: List<String>,
    ) {
        setInboxPage(result.page)
        setInboxHasMore(result.hasMore)
        val nextSnapshot = mergedInboxState(result.entries, markAsSeen = markAsSeen)
        persistInboxStateSnapshot(nextSnapshot)
        setInboxEntries(nextSnapshot.entries)
        setSeenInboxMessageIds(nextSnapshot.seenMessageIds)
        setHiddenSeenInboxMessageIds(hiddenSeenMessageIds)
        setUnreadInboxCount(unreadCount(nextSnapshot))
    }

    fun refreshInbox(openAfterLoad: Boolean) {
        if (isInboxLoading || isInboxAppending) return
        val currentSnapshot = loadInboxStateSnapshot()
        val refreshFromTime = currentSnapshot.nextRefreshFromTime()
        val willBeOpen = openAfterLoad || isInboxOpenRef.current == true
        val hiddenSeenMessageIds =
            if (isInboxOpenRef.current == true) {
                hiddenSeenInboxMessageIds
            } else {
                currentSnapshot.seenMessageIds
            }
        setInboxOpen(willBeOpen)
        setInboxLoading(true)
        setInboxAppending(false)
        setInboxRefreshFromTime(refreshFromTime)
        setInboxError(null)
        scope.launch {
            val result: AdminMessageListResult? =
                runCatching { adminMessageInboxService.fetch(page = 1, fromTime = refreshFromTime) }.getOrNull()
            setInboxLoading(false)
            if (result == null) {
                setInboxEntries(currentSnapshot.entries)
                setSeenInboxMessageIds(currentSnapshot.seenMessageIds)
                setHiddenSeenInboxMessageIds(hiddenSeenMessageIds)
                setInboxError(translator.translate("connected.messages.inbox.error"))
            } else {
                applyInboxPage(result, markAsSeen = willBeOpen, hiddenSeenMessageIds = hiddenSeenMessageIds)
            }
        }
    }

    fun loadMoreInbox() {
        if (!isInboxOpen || isInboxLoading || isInboxAppending || !inboxHasMore) return
        inboxScrollRestoreRef.current = inboxPopoverRef.current?.scrollTop
        setInboxAppending(true)
        setInboxError(null)
        val nextPage = inboxPage + 1
        scope.launch {
            val result: AdminMessageListResult? =
                runCatching { adminMessageInboxService.fetch(page = nextPage, fromTime = inboxRefreshFromTime) }.getOrNull()
            setInboxAppending(false)
            if (result == null) {
                setInboxError(translator.translate("connected.messages.inbox.error"))
            } else {
                applyInboxPage(result, markAsSeen = true, hiddenSeenMessageIds = hiddenSeenInboxMessageIds)
            }
        }
    }

    fun loadInbox() {
        refreshInbox(openAfterLoad = true)
    }

    fun closeInbox() {
        setInboxOpen(false)
    }

    fun applyComplaintQueuePage(
        result: OpenPostComplaintsResult,
        append: Boolean,
    ) {
        val currentEntries = complaintQueueEntriesRef.current ?: emptyList()
        val processedEntries = currentEntries.filterNot(ComplaintQueueEntry::isOpen)
        val openEntries =
            if (append) {
                currentEntries.filter(ComplaintQueueEntry::isOpen) + result.entries.map(OpenPostComplaint::toQueueEntry)
            } else {
                result.entries.map(OpenPostComplaint::toQueueEntry)
            }
        val mergedEntries = (openEntries + processedEntries).distinctBy(ComplaintQueueEntry::complaintId)
        complaintQueueEntriesRef.current = mergedEntries
        setComplaintQueueEntries(mergedEntries)
        setComplaintQueuePage(result.page)
        setComplaintQueueHasMore(result.hasMore)
    }

    fun refreshComplaintCount(forceRefresh: Boolean = false) {
        if (!adminPresentation.showsConnectedAdminBadge) {
            setOpenComplaintCount(0)
            return
        }
        scope.launch {
            val result = runCatching { adminPostComplaintService.countOpen(forceRefresh = forceRefresh) }.getOrNull()
            setOpenComplaintCount(result?.count ?: 0)
        }
    }

    fun refreshComplaintQueue(
        openAfterLoad: Boolean,
        includeProcessed: Boolean = showProcessedComplaintEntries,
    ) {
        if (isComplaintQueueLoading || isComplaintQueueAppending) return
        setComplaintQueueOpen(openAfterLoad || isComplaintQueueOpen)
        setComplaintQueueLoading(true)
        setComplaintQueueAppending(false)
        setComplaintQueueError(null)
        scope.launch {
            val result = runCatching { adminPostComplaintService.listOpen(page = 1, includeProcessed = includeProcessed) }.getOrNull()
            setComplaintQueueLoading(false)
            if (result == null) {
                setComplaintQueueEntries(emptyList())
                setComplaintQueuePage(1)
                setComplaintQueueHasMore(false)
                setComplaintQueueError(translator.translate("connected.complaints.admin.error"))
            } else {
                applyComplaintQueuePage(result, append = false)
            }
            refreshComplaintCount(forceRefresh = true)
        }
    }

    fun loadComplaintQueue() {
        refreshComplaintQueue(openAfterLoad = true)
    }

    fun closeComplaintQueue() {
        setComplaintQueueOpen(false)
    }

    fun toggleProcessedComplaintVisibility() {
        val willShowProcessedEntries = !showProcessedComplaintEntries
        setShowProcessedComplaintEntries(willShowProcessedEntries)
        if (isComplaintQueueOpen) {
            refreshComplaintQueue(openAfterLoad = true, includeProcessed = willShowProcessedEntries)
        }
    }

    fun closeComplaintComposer() {
        if (isComplaintSubmitting) return
        setComplaintComposerPostId(null)
        setComplaintComposerText("")
        setComplaintComposerError(null)
    }

    fun closeComplaintDecisionComposer() {
        if (isComplaintDecisionSubmitting) return
        setComplaintDecisionEntryId(null)
        setComplaintDecisionTargetStatus(null)
        setComplaintDecisionText("")
        setComplaintDecisionError(null)
    }

    fun loadMoreComplaintQueue() {
        if (!isComplaintQueueOpen || isComplaintQueueLoading || isComplaintQueueAppending || !complaintQueueHasMore) return
        setComplaintQueueAppending(true)
        setComplaintQueueError(null)
        val nextPage = complaintQueuePage + 1
        val includeProcessed = showProcessedComplaintEntries
        scope.launch {
            val result = runCatching { adminPostComplaintService.listOpen(page = nextPage, includeProcessed = includeProcessed) }.getOrNull()
            setComplaintQueueAppending(false)
            if (result == null) {
                setComplaintQueueError(translator.translate("connected.complaints.admin.error"))
            } else {
                applyComplaintQueuePage(result, append = true)
            }
        }
    }

    fun requestPostComplaint(entry: QueryResult.Entry) {
        if (complaintActionPostId != null || complaintComposerPostId != null || props.profile.connectedIdentityGid == null) return
        if (entry.authorGID == props.profile.connectedIdentityGid) return
        setComplaintActionPostId(entry.id)
        scope.launch {
            try {
                val status = postComplaintService.status(entry.id)
                when (status?.availability) {
                    PostComplaintAvailability.OPEN -> {
                        showToast(translator.translate("connected.complaints.post.open"))
                    }

                    PostComplaintAvailability.BLOCKED -> {
                        showToast(translator.translate("connected.complaints.post.blocked"))
                    }

                    PostComplaintAvailability.AVAILABLE -> {
                        setComplaintComposerPostId(entry.id)
                        setComplaintComposerText("")
                        setComplaintComposerError(null)
                    }

                    null -> {
                        showToast(translator.translate("connected.complaints.post.error"))
                    }
                }
            } finally {
                setComplaintActionPostId(null)
            }
        }
    }

    fun submitPostComplaint() {
        val postId = complaintComposerPostId ?: return
        val reason = complaintComposerText.trim()
        if (reason.isEmpty()) {
            setComplaintComposerError(translator.translate("connected.complaints.post.reasonRequired"))
            return
        }
        if (500 < reason.length) {
            setComplaintComposerError(translator.translate("connected.complaints.post.reasonTooLong"))
            return
        }
        setComplaintSubmitting(true)
        setComplaintComposerError(null)
        scope.launch {
            val created = runCatching { postComplaintService.submit(postId, reason) }.getOrDefault(false)
            setComplaintSubmitting(false)
            if (created) {
                closeComplaintComposer()
                showToast(translator.translate("connected.complaints.post.sent"))
                refreshComplaintCount(forceRefresh = true)
            } else {
                setComplaintComposerError(translator.translate("connected.complaints.post.error"))
            }
        }
    }

    fun startComplaintDecision(
        entry: ComplaintQueueEntry,
        targetStatus: PostComplaintState,
    ) {
        if (!entry.canTransitionTo(targetStatus)) return
        if (isComplaintDecisionSubmitting) return
        setComplaintDecisionEntryId(entry.complaintId)
        setComplaintDecisionTargetStatus(targetStatus)
        setComplaintDecisionText("")
        setComplaintDecisionError(null)
    }

    fun submitComplaintDecision() {
        val complaintId = complaintDecisionEntryId ?: return
        val targetStatus = complaintDecisionTargetStatus ?: return
        val entry = complaintQueueEntries.firstOrNull { it.complaintId == complaintId } ?: return
        val feedback = complaintDecisionText.trim()
        if (feedback.isEmpty()) {
            setComplaintDecisionError(translator.translate("connected.complaints.admin.feedbackRequired"))
            return
        }
        if (1000 < feedback.length) {
            setComplaintDecisionError(translator.translate("connected.complaints.admin.feedbackTooLong"))
            return
        }
        setComplaintDecisionSubmitting(true)
        setComplaintDecisionError(null)
        scope.launch {
            val success =
                adminPostComplaintService.decide(
                    complaintId = entry.complaintId,
                    postId = entry.complaint.postId,
                    targetStatus = targetStatus,
                    feedback = feedback,
                )
            setComplaintDecisionSubmitting(false)
            if (success) {
                val updatedEntries =
                    (complaintQueueEntriesRef.current ?: emptyList()).map { currentEntry ->
                        if (currentEntry.complaintId == complaintId) {
                            currentEntry.copy(state = targetStatus)
                        } else {
                            currentEntry
                        }
                    }
                complaintQueueEntriesRef.current = updatedEntries
                setComplaintQueueEntries(updatedEntries)
                closeComplaintDecisionComposer()
                showToast(translator.translate("connected.complaints.admin.decided"))
                refreshComplaintQueue(openAfterLoad = complaintQueueEntries.size > 1)
                refreshInbox(openAfterLoad = false)
            } else {
                setComplaintDecisionError(translator.translate("connected.complaints.admin.error"))
            }
        }
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

    fun updateTopicFilterState(filters: Map<String, Float>) {
        if (topics.isEmpty()) return
        val nextTopics =
            topics.map { topic ->
                val nextFilter = filters[topic.title]
                if (topic.scoreMustBeAbove == nextFilter) {
                    topic
                } else {
                    topic.copy(scoreMustBeAbove = nextFilter)
                }
            }
        if (nextTopics == topics) return
        val selectedTitle = selectedTopic?.title
        setTopics(nextTopics)
        if (selectedTitle != null) {
            val refreshed = nextTopics.firstOrNull { it.title == selectedTitle } ?: return
            setSelectedTopic(refreshed)
        }
    }

    fun clearDeleteCandidate() {
        if (isDeletingPost) return
        setDeleteCandidate(null)
        setDeleteError(null)
    }

    fun removePostFromState(postId: String) {
        val nextPosts = removePostsFromTopicState(posts, setOf(postId))
        setPosts(nextPosts)
        setSelectedPost(selectedPost.takeUnless { it?.id == postId })
        setReplies(removeRepliesFromStateEntries(replies, setOf(postId)))
        if (deleteCandidate?.id == postId) {
            setDeleteCandidate(null)
        }
    }

    fun removePostsFromState(postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        val idSet = postIds.toSet()
        val nextPosts = removePostsFromTopicState(posts, idSet)
        setPosts(nextPosts)
        if (selectedPost?.id in idSet) {
            setSelectedPost(null)
            setReplies(null)
        } else {
            setReplies(removeRepliesFromStateEntries(replies, idSet))
        }
        if (deleteCandidate?.id in idSet) {
            setDeleteCandidate(null)
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

    fun setAppendingState(active: Boolean) {
        isAppendingPostsRef.current = active
    }

    fun setPaneForSelectedTopic(topic: Topic?) {
        if (!isMobile) return
        val nextPane = if (topic == null) MobilePane.Topics else MobilePane.Posts
        setMobilePane(nextPane)
    }

    fun setPaneForSelectedPost(entry: QueryResult.Entry?) {
        if (!isMobile) return
        val nextPane = if (entry == null) MobilePane.Posts else MobilePane.Replies
        setMobilePane(nextPane)
    }

    fun resetReplyComposer() {
        setReplyComposerOpen(false)
        setReplyComposerText("")
        setReplyPublishError(null)
        setReplyPublishing(false)
    }

    fun clearReplies() {
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        setSelectedPost(null)
        setReplies(null)
        setLoadingReplies(false)
        isAppendingRepliesRef.current = false
        resetReplyComposer()
    }

    fun updateTopicPosts(updated: TopicPosts) {
        setPosts { current -> replaceTopicPosts(current, updated) }
    }

    suspend fun refreshPosts(currentTopic: Topic?): Boolean {
        val nextVersion = (postsRequestVersion.current ?: 0) + 1
        postsRequestVersion.current = nextVersion
        val version = nextVersion
        postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        if (currentTopic == null) {
            setPosts(emptyList())
            setLoadingPosts(false)
            return true
        }
        setAppendingState(false)
        setLoadingPosts(true)
        val results =
            runCatching { searchService.fetchFor(listOf(currentTopic)) }
                .getOrElse {
                    if (postsRequestVersion.current == version) {
                        setLoadingPosts(false)
                    }
                    return false
                }
        if (postsRequestVersion.current != version) return false
        setPosts(results)
        setLoadingPosts(false)
        return true
    }

    useEffect(props.profile.snapshotRefreshVersion) {
        val nextVersion = props.profile.snapshotRefreshVersion
        if (nextVersion == snapshotRefreshVersionRef.current) return@useEffect
        snapshotRefreshVersionRef.current = nextVersion
        val topicForRefresh = selectedTopic
        clearReplies()
        scope.launch {
            postCache.clearAll()
            replyCache.clearAll()
            val refreshed = refreshPosts(topicForRefresh)
            if (!refreshed && selectedTopic?.title == topicForRefresh?.title) {
                showToast(translator.translate("connected.composer.refresh.error"))
            }
        }
    }

    useEffect(props.profile.connectedIdentityGid) {
        val storedSnapshot = loadInboxStateSnapshot()
        setInboxEntries(storedSnapshot.entries)
        setSeenInboxMessageIds(storedSnapshot.seenMessageIds)
        setHiddenSeenInboxMessageIds(storedSnapshot.seenMessageIds)
        setShowSeenInboxEntries(false)
        setUnreadInboxCount(unreadCount(storedSnapshot))
        setInboxError(null)
        val nextConnectedIdentityGid = props.profile.connectedIdentityGid
        if (nextConnectedIdentityGid == connectedIdentityGidRef.current) return@useEffect
        connectedIdentityGidRef.current = nextConnectedIdentityGid
        if (nextConnectedIdentityGid == null) {
            setInboxLoading(false)
            setInboxOpen(false)
            return@useEffect
        }
        refreshInbox(openAfterLoad = false)
    }

    fun persistFilters(updated: Map<String, Float>) {
        val sanitized = TopicFilters.sanitize(updated)
        if (sanitized == topicFilters) return
        updateTopicFilterState(sanitized)
        setTopicFilters(sanitized)
        scope.launch {
            repository.applyFilters(sanitized)
        }
    }

    fun selectTopic(topic: Topic?) {
        if (topic == selectedTopic) {
            if (isMobile && topic != null) {
                clearReplies()
                setPaneForSelectedTopic(topic)
            }
            return
        }
        clearReplies()
        setPaneForSelectedTopic(topic)
        setSelectedTopic(topic)
        scope.launch {
            persistSessionSnapshot { snapshot ->
                IdentitySessionSnapshot(
                    connectedIdentityGid = snapshot?.connectedIdentityGid,
                    isProfileVisible = snapshot?.isProfileVisible ?: false,
                    selectedTopicTitle = topic?.title,
                )
            }
            refreshPosts(topic)
        }
    }

    useEffect(*noDependencies) {
        scope.launch {
            val loadedTopics = repository.all()
            setTopics(loadedTopics)
            val filters = extractTopicFilters(loadedTopics)
            setTopicFilters(filters)
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            val storedSelection = snapshot?.selectedTopicTitle
            val initialSelection =
                loadedTopics.firstOrNull { it.title == storedSelection } ?: loadedTopics.firstOrNull()
            selectTopic(initialSelection)
        }
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
        val inboxOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if (isInboxOpenRef.current != true) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedInboxButton = inboxButtonRef.current?.contains(target) == true
            val clickedInboxPopover = inboxPopoverRef.current?.contains(target) == true
            if (!clickedInboxButton && !clickedInboxPopover) {
                closeInbox()
            }
        }
        document.addEventListener("mousedown", inboxOutsideClickListener)
        val complaintQueueOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if (isComplaintQueueOpenRef.current != true) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedComplaintQueueButton = complaintQueueButtonRef.current?.contains(target) == true
            val clickedComplaintQueuePopover = complaintQueuePopoverRef.current?.contains(target) == true
            if (!clickedComplaintQueueButton && !clickedComplaintQueuePopover) {
                closeComplaintQueue()
            }
        }
        document.addEventListener("mousedown", complaintQueueOutsideClickListener)
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
            document.removeEventListener("mousedown", inboxOutsideClickListener)
            document.removeEventListener("mousedown", complaintQueueOutsideClickListener)
            document.removeEventListener("mousedown", adminUserCardOutsideClickListener)
            clearProfileExitTimer()
            clearProfileEntryTimer()
            clearToastTimer()
            scope.cancel()
        }
    }

    useEffect(arrayOf(adminPresentation.showsConnectedAdminBadge, props.profile.connectedIdentityGid)) {
        refreshComplaintCount()
    }

    val avatarDescription = translator.translate(adminPresentation.avatarTitleKey)
    val visibleInboxEntries =
        inboxEntries.filteredForInbox(
            showSeenMessages = showSeenInboxEntries,
            hiddenSeenMessageIds = hiddenSeenInboxMessageIds,
        )
    val hasHiddenSeenInboxEntries = hiddenSeenInboxMessageIds.isNotEmpty()
    val hasReadInboxEntries = seenInboxMessageIds.isNotEmpty()
    val visibleComplaintQueueEntries = complaintQueueEntries.filteredForComplaintQueue(showProcessedComplaintEntries)
    val hasHiddenProcessedComplaintEntries = complaintQueueEntries.any { !it.isOpen() }

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

    fun addTopic() {
        val text = window.prompt(translator.translate("connected.topics.prompt")) ?: return
        val sanitized = text.trim()
        if (sanitized.isEmpty()) return
        scope.launch {
            val updated = repository.add(sanitized)
            setTopics(updated)
            selectTopic(updated.lastOrNull())
        }
    }

    fun removeTopic(topic: Topic) {
        val removalIndex = topics.indexOfFirst { it.title == topic.title }
        val wasSelected = selectedTopic?.title == topic.title
        if (removalIndex < 0) return
        scope.launch {
            val updated = repository.remove(topic)
            setTopics(updated)
            if (topicFilters.containsKey(topic.title)) {
                persistFilters(topicFilters - topic.title)
            }
            if (wasSelected) {
                selectTopic(updated.getOrNull(removalIndex))
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

    fun openComposer() {
        setPublishError(null)
        setComposerOpen(true)
    }

    fun closeComposer() {
        if (isPublishing) return
        setPublishError(null)
        setComposerOpen(false)
    }

    fun sendPost() {
        if (isPublishing) return
        val text = composerText.trim()
        if (text.isEmpty()) return
        val topicForRefresh = selectedTopic
        setPublishing(true)
        setPublishError(null)
        scope.launch {
            try {
                val success = postPublisher.publish(text)
                if (success) {
                    setComposerText("")
                    setComposerOpen(false)
                    topicForRefresh?.let { topic ->
                        runCatching { postCache.clear(topic.title) }
                    }
                    val refreshed = refreshPosts(topicForRefresh)
                    if (!refreshed && selectedTopic?.title == topicForRefresh?.title) {
                        showToast(translator.translate("connected.composer.refresh.error"))
                    }
                } else {
                    setPublishError(translator.translate("connected.composer.error"))
                }
            } finally {
                setPublishing(false)
            }
        }
    }

    fun requestDelete(entry: QueryResult.Entry) {
        if (isDeletingPost) return
        setDeleteCandidate(entry)
        setDeleteError(null)
    }

    fun confirmDelete(entry: QueryResult.Entry) {
        if (isDeletingPost) return
        setDeletingPost(true)
        setDeleteError(null)
        scope.launch {
            val success = postDeletionService.delete(entry.id)
            setDeletingPost(false)
            if (success) {
                removePostFromState(entry.id)
            } else {
                setDeleteError(translator.translate("connected.posts.delete.error"))
            }
        }
    }

    fun deleteAllPosts() {
        if (isDeletingAllPosts) return
        setDeletingAllPosts(true)
        scope.launch {
            val deletedIds = allPostsDeletionService.deleteAll()
            setDeletingAllPosts(false)
            if (deletedIds == null) {
                showToast(translator.translate("identity.posts.delete.error"))
            } else {
                removePostsFromState(deletedIds)
                showToast(translator.translate("identity.posts.delete.success"))
            }
        }
    }

    fun openReplyComposer() {
        if (selectedPost == null || isReplyPublishing) return
        setReplyPublishError(null)
        setReplyComposerOpen(true)
    }

    fun closeReplyComposer() {
        if (isReplyPublishing) return
        setReplyPublishError(null)
        setReplyComposerOpen(false)
    }

    fun sendReply() {
        if (isReplyPublishing) return
        val targetPost = selectedPost ?: return
        val text = replyComposerText.trim()
        if (text.isEmpty()) return
        val rootId = rootIdFor(targetPost)
        setReplyPublishing(true)
        setReplyPublishError(null)
        scope.launch {
            val published = replyPublisher.publish(text, rootId)
            if (!published) {
                setReplyPublishError(translator.translate("connected.replies.compose.error"))
                setReplyPublishing(false)
                return@launch
            }
            setReplyComposerText("")
            setReplyComposerOpen(false)
            val currentRoot = selectedPost.let { rootIdFor(it) }
            if (currentRoot != rootId) {
                setReplyPublishing(false)
                return@launch
            }
            val nextVersion = (repliesRequestVersion.current ?: 0) + 1
            repliesRequestVersion.current = nextVersion
            setLoadingReplies(true)
            repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
            val refreshed =
                runCatching { replySearchService.fetchFor(rootId) }
                    .getOrElse {
                        if (repliesRequestVersion.current == nextVersion) {
                            setLoadingReplies(false)
                        }
                        null
                    }
            if (repliesRequestVersion.current != nextVersion) {
                setReplyPublishing(false)
                return@launch
            }
            if (refreshed != null) {
                setReplies(refreshed)
            }
            setLoadingReplies(false)
            setReplyPublishing(false)
        }
    }

    fun clearTopicFilter(topic: Topic) {
        val previousFilter = topicFilters[topic.title] ?: return
        persistFilters(topicFilters - topic.title)
        val clearedTopic = topic.copy(scoreMustBeAbove = null)
        scope.launch {
            searchService.filterCleared(clearedTopic, previousFilter)
            if (selectedTopic?.title == topic.title) {
                refreshPosts(clearedTopic)
            }
        }
    }

    fun toggleTopicFilter(topic: Topic, entry: QueryResult.Entry) {
        val currentScore = topicFilters[topic.title]
        if (currentScore != null && currentScore == entry.similarity) {
            clearTopicFilter(topic)
            return
        }
        persistFilters(topicFilters + (topic.title to entry.similarity))
    }

    fun loadMorePosts() {
        val topic = selectedTopic ?: return
        if (isLoadingPosts || isAppendingPostsRef.current == true) return
        val currentPosts = posts.firstOrNull { it.topic.title == topic.title } ?: return
        if (currentPosts.entries.isEmpty()) return
        val hasAnchorEntry =
            currentPosts.entries.any { entry ->
                val threshold = topic.scoreMustBeAbove
                threshold == null || entry.similarity > threshold
            }
        if (!hasAnchorEntry) return
        postsContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        setAppendingState(true)
        scope.launch {
            try {
                val additionalPosts = searchService.fetchMore(topic)
                if (additionalPosts != null) {
                    updateTopicPosts(additionalPosts)
                }
            } finally {
                setAppendingState(false)
            }
        }
    }

    fun loadMoreReplies() {
        val rootId = replies?.rootId ?: return
        if (isLoadingReplies || isAppendingRepliesRef.current == true) return
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        isAppendingRepliesRef.current = true
        scope.launch {
            try {
                val additionalReplies = replySearchService.fetchMore(rootId)
                if (additionalReplies != null) {
                    setReplies(additionalReplies)
                }
            } finally {
                isAppendingRepliesRef.current = false
            }
        }
    }

    fun selectPost(entry: QueryResult.Entry) {
        resetReplyComposer()
        val rootId = rootIdFor(entry)
        val nextVersion = (repliesRequestVersion.current ?: 0) + 1
        repliesRequestVersion.current = nextVersion
        setSelectedPost(entry)
        setPaneForSelectedPost(entry)
        setReplies(null)
        repliesContainerRef.current?.removeAttribute("data-mosa-scroll-ready")
        isAppendingRepliesRef.current = false
        setLoadingReplies(true)
        scope.launch {
            val result =
                runCatching { replySearchService.fetchFor(rootId) }
                    .getOrElse {
                        if (repliesRequestVersion.current == nextVersion) {
                            setReplies(null)
                            setLoadingReplies(false)
                        }
                        return@launch
                    }
            if (repliesRequestVersion.current != nextVersion) return@launch
            setReplies(result)
            setLoadingReplies(false)
        }
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
                        loadMorePosts()
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
                        loadMoreReplies()
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

    useEffect(arrayOf(inboxEntries, isInboxAppending)) {
        val scrollTop = inboxScrollRestoreRef.current ?: return@useEffect
        inboxPopoverRef.current?.scrollTop = scrollTop
        if (!isInboxAppending) {
            inboxScrollRestoreRef.current = null
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
                            closeInbox()
                        } else {
                            loadInbox()
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
                                closeComplaintQueue()
                            } else {
                                loadComplaintQueue()
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
                        { setShowSeenInboxEntries { current -> !current } },
                        { closeInbox() },
                        ::loadMoreInbox,
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
                        onToggleProcessedVisibility = ::toggleProcessedComplaintVisibility,
                        onClose = ::closeComplaintQueue,
                        onLoadMore = ::loadMoreComplaintQueue,
                        decisionEntryId = complaintDecisionEntryId,
                        decisionTargetStatus = complaintDecisionTargetStatus,
                        decisionText = complaintDecisionText,
                        decisionErrorText = complaintDecisionError,
                        isDecisionSubmitting = isComplaintDecisionSubmitting,
                        onDecisionTextChange = {
                            setComplaintDecisionText(it)
                            if (complaintDecisionError != null) {
                                setComplaintDecisionError(null)
                            }
                        },
                        onCloseDecisionComposer = ::closeComplaintDecisionComposer,
                        onSubmitDecision = ::submitComplaintDecision,
                        onConfirm = { startComplaintDecision(it, PostComplaintState.CONFIRMED) },
                        onReject = { startComplaintDecision(it, PostComplaintState.REJECTED) },
                        onDelete = { startComplaintDecision(it, PostComplaintState.DELETED) },
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
                        targets.topic = findTopicByTitle(topics, topicTitle)
                        targets.post = findPostById(posts, postId)
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
                                    selectTopic(target)
                                    setMobilePane(MobilePane.Posts)
                                }
                                MobilePane.Posts -> targets.post?.let { post ->
                                    selectPost(post)
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
                topicColumn(translator, topics, selectedTopic, ::addTopic, ::selectTopic, ::removeTopic)
                postsColumn(
                    translator = translator,
                    visualsFactory = visualsFactory,
                    posts = posts,
                    selectedTopic = selectedTopic,
                    selectedPost = selectedPost,
                    isLoading = isLoadingPosts,
                    activeFilters = topicFilters,
                    onToggleFilter = ::toggleTopicFilter,
                    onClearFilter = ::clearTopicFilter,
                    onSelectPost = ::selectPost,
                    connectedGid = props.profile.connectedIdentityGid,
                    canDeleteAsAdmin = adminPresentation.canDeleteAsAdmin,
                    deleteCandidateId = deleteCandidate?.id,
                    isDeleting = isDeletingPost,
                    deleteError = deleteError,
                    onRequestDelete = ::requestDelete,
                    onCancelDelete = ::clearDeleteCandidate,
                    onConfirmDelete = ::confirmDelete,
                    complaintActionPostId = complaintActionPostId,
                    complaintComposerPostId = complaintComposerPostId,
                    complaintComposerText = complaintComposerText,
                    complaintComposerError = complaintComposerError,
                    isComplaintSubmitting = isComplaintSubmitting,
                    onRequestComplaint = ::requestPostComplaint,
                    onComplaintTextChange = {
                        setComplaintComposerText(it)
                        if (complaintComposerError != null) {
                            setComplaintComposerError(null)
                        }
                    },
                    onCloseComplaintComposer = ::closeComplaintComposer,
                    onSubmitComplaint = ::submitPostComplaint,
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
                    onRequestDelete = ::requestDelete,
                    onCancelDelete = ::clearDeleteCandidate,
                    onConfirmDelete = ::confirmDelete,
                    complaintActionPostId = complaintActionPostId,
                    complaintComposerPostId = complaintComposerPostId,
                    complaintComposerText = complaintComposerText,
                    complaintComposerError = complaintComposerError,
                    isComplaintSubmitting = isComplaintSubmitting,
                    onRequestComplaint = ::requestPostComplaint,
                    onComplaintTextChange = {
                        setComplaintComposerText(it)
                        if (complaintComposerError != null) {
                            setComplaintComposerError(null)
                        }
                    },
                    onCloseComplaintComposer = ::closeComplaintComposer,
                    onSubmitComplaint = ::submitPostComplaint,
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
                    onOpenComposer = ::openReplyComposer,
                    onCloseComposer = ::closeReplyComposer,
                    onReplyTextChange = { next -> setReplyComposerText(next) },
                    onSendReply = ::sendReply,
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
                onActivate = ::openComposer,
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
            onDeletePosts = ::deleteAllPosts
            exitAnimation = profileExitAnimation
            entryAnimation = profileEntryAnimation
        }
    }

    if (isComposerOpen) {
        composerDialog(
            translator = translator,
            text = composerText,
            onTextChange = { next -> setComposerText(next) },
            onClose = ::closeComposer,
            onSend = ::sendPost,
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
