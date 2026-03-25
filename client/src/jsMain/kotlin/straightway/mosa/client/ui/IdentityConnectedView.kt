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

private data class ComplaintQueueEntry(
    val complaint: OpenPostComplaint,
    val state: PostComplaintState,
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

    fun refreshComplaintQueue(openAfterLoad: Boolean) {
        if (isComplaintQueueLoading || isComplaintQueueAppending) return
        setComplaintQueueOpen(openAfterLoad || isComplaintQueueOpen)
        setComplaintQueueLoading(true)
        setComplaintQueueAppending(false)
        setComplaintQueueError(null)
        scope.launch {
            val result = runCatching { adminPostComplaintService.listOpen(page = 1) }.getOrNull()
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
        setShowProcessedComplaintEntries { current -> !current }
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
        scope.launch {
            val result = runCatching { adminPostComplaintService.listOpen(page = nextPage) }.getOrNull()
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
        entry: OpenPostComplaint,
        targetStatus: PostComplaintState,
    ) {
        if (targetStatus == PostComplaintState.OPEN) return
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

    fun filtersFromTopics(items: List<Topic>): Map<String, Float> =
        items.mapNotNull { topic ->
            val value = topic.scoreMustBeAbove
            if (value == null) {
                null
            } else {
                topic.title to value
            }
        }.toMap()

    fun rootIdFor(entry: QueryResult.Entry): String = entry.rootId ?: entry.id
    fun findTopicByTitle(title: String?): Topic? =
        title?.let { topicTitle -> topics.firstOrNull { it.title == topicTitle } }

    fun findPostById(id: String?): QueryResult.Entry? =
        id?.let { postId ->
            posts.firstNotNullOfOrNull { topicPosts ->
                topicPosts.entries.firstOrNull { it.id == postId }
            }
        }

    fun clearDeleteCandidate() {
        if (isDeletingPost) return
        setDeleteCandidate(null)
        setDeleteError(null)
    }

    fun removePostFromState(postId: String) {
        val nextPosts =
            posts.mapNotNull { topicPosts ->
                val filtered = topicPosts.entries.filterNot { it.id == postId }
                if (filtered.isEmpty()) {
                    null
                } else if (filtered.size == topicPosts.entries.size) {
                    topicPosts
                } else {
                    topicPosts.copy(entries = filtered)
                }
            }
        setPosts(nextPosts)
        if (selectedPost?.id == postId) {
            setSelectedPost(null)
            setReplies(null)
        }
        replies?.let { currentReplies ->
            if (currentReplies.entries.any { it.id == postId }) {
                val filtered = currentReplies.entries.filterNot { it.id == postId }
                setReplies(currentReplies.copy(entries = filtered))
            }
        }
        if (deleteCandidate?.id == postId) {
            setDeleteCandidate(null)
        }
    }

    fun removePostsFromState(postIds: Collection<String>) {
        if (postIds.isEmpty()) return
        val idSet = postIds.toSet()
        val nextPosts =
            posts.mapNotNull { topicPosts ->
                val filtered = topicPosts.entries.filterNot { it.id in idSet }
                if (filtered.isEmpty()) {
                    null
                } else if (filtered.size == topicPosts.entries.size) {
                    topicPosts
                } else {
                    topicPosts.copy(entries = filtered)
                }
            }
        setPosts(nextPosts)
        if (selectedPost?.id in idSet) {
            setSelectedPost(null)
            setReplies(null)
        }
        replies?.let { currentReplies ->
            if (currentReplies.entries.any { it.id in idSet }) {
                val filtered = currentReplies.entries.filterNot { it.id in idSet }
                setReplies(currentReplies.copy(entries = filtered))
            }
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
        setPosts { current ->
            val replaced =
                current
                    .map { topicPosts ->
                        if (topicPosts.topic.title == updated.topic.title) {
                            updated
                        } else {
                            topicPosts
                        }
                    }
            if (replaced.any { it.topic.title == updated.topic.title }) {
                replaced
            } else {
                replaced + updated
            }
        }
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
            val filters = filtersFromTopics(loadedTopics)
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
                        targets.topic = findTopicByTitle(topicTitle)
                        targets.post = findPostById(postId)
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

private fun ChildrenBuilder.topicColumn(
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

private fun ChildrenBuilder.postsColumn(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    posts: List<TopicPosts>,
    selectedTopic: Topic?,
    selectedPost: QueryResult.Entry?,
    isLoading: Boolean,
    activeFilters: Map<String, Float>,
    onToggleFilter: (Topic, QueryResult.Entry) -> Unit,
    onClearFilter: (Topic) -> Unit,
    onSelectPost: (QueryResult.Entry) -> Unit,
    connectedGid: String?,
    canDeleteAsAdmin: Boolean,
    deleteCandidateId: String?,
    isDeleting: Boolean,
    deleteError: String?,
    onRequestDelete: (QueryResult.Entry) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (QueryResult.Entry) -> Unit,
    complaintActionPostId: String?,
    complaintComposerPostId: String?,
    complaintComposerText: String,
    complaintComposerError: String?,
    isComplaintSubmitting: Boolean,
    onRequestComplaint: (QueryResult.Entry) -> Unit,
    onComplaintTextChange: (String) -> Unit,
    onCloseComplaintComposer: () -> Unit,
    onSubmitComplaint: () -> Unit,
    canComposeAdminMessage: Boolean,
    adminUserCardAnchorId: String?,
    adminMessageRecipientName: String?,
    adminMessageSubject: String,
    adminMessageBody: String,
    isAdminMessageSending: Boolean,
    adminMessageError: String?,
    isAdminMessageComposerOpen: Boolean,
    onToggleAdminUserCard: (String, String, String, String) -> Unit,
    onOpenAdminMessageComposer: () -> Unit,
    onAdminMessageSubjectChange: (String) -> Unit,
    onAdminMessageBodyChange: (String) -> Unit,
    onCloseAdminMessageComposer: () -> Unit,
    onSendAdminMessage: () -> Unit,
    showBackToTopics: Boolean,
    onBackToTopics: () -> Unit,
    containerRef: Ref<HTMLDivElement>,
    loadMoreRef: Ref<HTMLDivElement>,
) = div {
    className = ClassName("topic-board__column topic-board__column--posts")
    ref = containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    val activeFilterTopic =
        selectedTopic
            ?.let { topic ->
                activeFilters[topic.title]?.let { topic to it }
            }
            ?: posts.firstNotNullOfOrNull { topicPosts ->
                activeFilters[topicPosts.topic.title]?.let { topicPosts.topic to it }
            }
    div {
        className = ClassName("topic-board__column-header")
        if (showBackToTopics) {
            button {
                className = ClassName("topic-board__back-button")
                ariaLabel = translator.translate("connected.mobile.backToTopics")
                onClick = { onBackToTopics() }
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
                        onClick = { onClearFilter(filterTopic) }
                        i {
                            className =
                                ClassName("fa fa-solid fa-arrows-up-to-line topic-board__filter-reset-icon")
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
    val highlightTopic = selectedTopic
    if (highlightTopic != null) {
        topicHighlightCard(translator, highlightTopic)
    }
    when {
        isLoading -> Unit
        posts.isEmpty() ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.posts.empty")
            }
        else ->
            posts.forEach { topicPosts ->
                val filterScore = activeFilters[topicPosts.topic.title]
                val filterResult = filterEntriesByScore(topicPosts.entries, filterScore)
                filterResult.entries.forEach { entry ->
                    val isFilterEntry = filterResult.filteredEntryId == entry.id
                    postCard(
                        translator = translator,
                        visualsFactory = visualsFactory,
                        entry = entry,
                        topic = topicPosts.topic,
                        isFilterEntry = isFilterEntry,
                        onToggleFilter = onToggleFilter,
                        isSelected = selectedPost?.id == entry.id,
                        onSelect = {
                            onCancelDelete()
                            onSelectPost(entry)
                        },
                        connectedGid = connectedGid,
                        canDeleteAsAdmin = canDeleteAsAdmin,
                        deleteCandidateId = deleteCandidateId,
                        isDeleting = isDeleting,
                        deleteError = deleteError,
                        onRequestDelete = { onRequestDelete(entry) },
                        onCancelDelete = { onCancelDelete() },
                        onConfirmDelete = { onConfirmDelete(entry) },
                        canComplain = connectedGid != null && entry.authorGID != connectedGid,
                        isComplaining = complaintActionPostId == entry.id,
                        complaintComposerPostId = complaintComposerPostId,
                        complaintComposerText = complaintComposerText,
                        complaintComposerError = complaintComposerError,
                        isComplaintSubmitting = isComplaintSubmitting,
                        onRequestComplaint = { onRequestComplaint(entry) },
                        onComplaintTextChange = onComplaintTextChange,
                        onCloseComplaintComposer = onCloseComplaintComposer,
                        onSubmitComplaint = onSubmitComplaint,
                        canComposeAdminMessage = canComposeAdminMessage,
                        onToggleAdminUserCard = onToggleAdminUserCard,
                    )
                }
            }
    }
    if (!isLoading && posts.isNotEmpty()) {
        div {
            ref = loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
}

private fun ChildrenBuilder.repliesColumn(
    translator: Translator,
    visualsFactory: IdentityVisualsFactory,
    selectedPost: QueryResult.Entry?,
    replies: ThreadReplies?,
    isLoading: Boolean,
    connectedGid: String?,
    canDeleteAsAdmin: Boolean,
    deleteCandidateId: String?,
    isDeleting: Boolean,
    deleteError: String?,
    onRequestDelete: (QueryResult.Entry) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (QueryResult.Entry) -> Unit,
    complaintActionPostId: String?,
    complaintComposerPostId: String?,
    complaintComposerText: String,
    complaintComposerError: String?,
    isComplaintSubmitting: Boolean,
    onRequestComplaint: (QueryResult.Entry) -> Unit,
    onComplaintTextChange: (String) -> Unit,
    onCloseComplaintComposer: () -> Unit,
    onSubmitComplaint: () -> Unit,
    isComposerOpen: Boolean,
    replyText: String,
    canActivateComposer: Boolean,
    isPublishing: Boolean,
    errorText: String?,
    onOpenComposer: () -> Unit,
    onCloseComposer: () -> Unit,
    onReplyTextChange: (String) -> Unit,
    onSendReply: () -> Unit,
    canComposeAdminMessage: Boolean,
    adminUserCardAnchorId: String?,
    adminMessageRecipientName: String?,
    adminMessageSubject: String,
    adminMessageBody: String,
    isAdminMessageSending: Boolean,
    adminMessageError: String?,
    isAdminMessageComposerOpen: Boolean,
    onToggleAdminUserCard: (String, String, String, String) -> Unit,
    onOpenAdminMessageComposer: () -> Unit,
    onAdminMessageSubjectChange: (String) -> Unit,
    onAdminMessageBodyChange: (String) -> Unit,
    onCloseAdminMessageComposer: () -> Unit,
    onSendAdminMessage: () -> Unit,
    isMobile: Boolean,
    showBackToPosts: Boolean,
    onBackToPosts: () -> Unit,
    containerRef: Ref<HTMLDivElement>,
    loadMoreRef: Ref<HTMLDivElement>,
) = div {
    className = ClassName("topic-board__column topic-board__column--replies")
    ref = containerRef
    onScroll = { event ->
        event.currentTarget.dataset["mosaScrollReady"] = "true"
    }
    div {
        className = ClassName("topic-board__column-header")
        if (isMobile && showBackToPosts) {
            button {
                className = ClassName("topic-board__back-button")
                ariaLabel = translator.translate("connected.mobile.backToPosts")
                onClick = { onBackToPosts() }
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
        selectedPost == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.empty")
            }
        isLoading && replies == null ->
            div {
                className = ClassName("topic-board__empty")
                +translator.translate("connected.replies.loading")
            }
        else -> {
            replyOriginCard(translator, visualsFactory, selectedPost)
            val replyEntries = replies?.entries.orEmpty()
            if (replyEntries.isEmpty() && !isLoading) {
                div {
                    className = ClassName("topic-board__empty")
                    +translator.translate("connected.replies.none")
                }
            }
            replyEntries.forEach { reply ->
                replyCard(
                    translator = translator,
                    visualsFactory = visualsFactory,
                    reply = reply,
                    connectedGid = connectedGid,
                    canDeleteAsAdmin = canDeleteAsAdmin,
                    deleteCandidateId = deleteCandidateId,
                    isDeleting = isDeleting,
                    deleteError = deleteError,
                    onRequestDelete = { onRequestDelete(reply) },
                    onCancelDelete = onCancelDelete,
                    onConfirmDelete = { onConfirmDelete(reply) },
                    canComplain = connectedGid != null && reply.authorGID != connectedGid,
                    isComplaining = complaintActionPostId == reply.id,
                    complaintComposerPostId = complaintComposerPostId,
                    complaintComposerText = complaintComposerText,
                    complaintComposerError = complaintComposerError,
                    isComplaintSubmitting = isComplaintSubmitting,
                    onRequestComplaint = { onRequestComplaint(reply) },
                    onComplaintTextChange = onComplaintTextChange,
                    onCloseComplaintComposer = onCloseComplaintComposer,
                    onSubmitComplaint = onSubmitComplaint,
                    canComposeAdminMessage = canComposeAdminMessage,
                    onToggleAdminUserCard = onToggleAdminUserCard,
                )
            }
        }
    }
    if (!isLoading && replies != null && replies.entries.isNotEmpty()) {
        div {
            ref = loadMoreRef
            className = ClassName("topic-board__load-sentinel")
        }
    }
    div {
        className = ClassName("reply-composer-wrapper")
        div {
            className = ClassName("reply-composer-layer")
            if (isComposerOpen) {
                replyComposerPopover(
                    translator = translator,
                    text = replyText,
                    isPublishing = isPublishing,
                    errorText = errorText,
                    onTextChange = onReplyTextChange,
                    onSend = onSendReply,
                    onClose = onCloseComposer,
                )
            }
            div {
                className = ClassName("reply-composer-fab")
                replyComposerButton(
                    label = translator.translate("connected.replies.compose.open"),
                    disabled = !canActivateComposer,
                    onActivate = onOpenComposer,
                )
            }
        }
    }
}

private fun ChildrenBuilder.replyComposerButton(
    label: String,
    disabled: Boolean,
    onActivate: () -> Unit,
) = button {
    className = ClassName("reply-composer-trigger")
    this.disabled = disabled
    ariaLabel = label
    title = label
    onClick = { onActivate() }
    i {
        className = ClassName("fa fa-solid fa-reply reply-composer-trigger__icon")
    }
}

private fun ChildrenBuilder.replyComposerPopover(
    translator: Translator,
    text: String,
    isPublishing: Boolean,
    errorText: String?,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) = div {
    className = ClassName("reply-composer")
    div {
        className = ClassName("reply-composer__header")
        span {
            className = ClassName("reply-composer__title")
            +translator.translate("connected.replies.compose.title")
        }
        button {
            className = ClassName("reply-composer__close")
            ariaLabel = translator.translate("connected.replies.compose.close")
            onClick = { onClose() }
            +"×"
        }
    }
    textarea {
        className = ClassName("reply-composer__textarea")
        placeholder = translator.translate("connected.replies.compose.placeholder")
        value = text
        disabled = isPublishing
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            onTextChange(event.currentTarget.value)
        }
    }
    if (errorText != null) {
        div {
            className = ClassName("reply-composer__error")
            +errorText
        }
    }
    div {
        className = ClassName("reply-composer__actions")
        button {
            className = ClassName("button button--primary reply-composer__send")
            disabled = text.trim().isEmpty() || isPublishing
            onClick = { onSend() }
            +translator.translate("connected.replies.compose.send")
        }
    }
}

private fun ChildrenBuilder.complaintComposerPopover(
    translator: Translator,
    title: String,
    placeholder: String,
    text: String,
    isSubmitting: Boolean,
    errorText: String?,
    sendLabel: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) = div {
    className = ClassName("complaint-composer__panel")
    div {
        className = ClassName("complaint-composer__header")
        span {
            className = ClassName("complaint-composer__title")
            +title
        }
        button {
            className = ClassName("complaint-composer__close")
            ariaLabel = translator.translate("connected.complaints.compose.close")
            onClick = { onClose() }
            +"×"
        }
    }
    textarea {
        className = ClassName("complaint-composer__textarea")
        this.placeholder = placeholder
        value = text
        disabled = isSubmitting
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            onTextChange(event.currentTarget.value)
        }
    }
    if (errorText != null) {
        div {
            className = ClassName("complaint-composer__error")
            +errorText
        }
    }
    div {
        className = ClassName("complaint-composer__actions")
        button {
            className = ClassName("button button--secondary complaint-composer__cancel")
            disabled = isSubmitting
            onClick = { onClose() }
            +translator.translate("connected.complaints.compose.cancel")
        }
        button {
            className = ClassName("button button--primary complaint-composer__send")
            disabled = text.trim().isEmpty() || isSubmitting
            onClick = { onSend() }
            +sendLabel
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

private fun ChildrenBuilder.adminMessageInboxPopover(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    entries: List<AdminMessage>,
    isLoading: Boolean,
    isAppending: Boolean,
    errorText: String?,
    hasMore: Boolean,
    showSeenMessages: Boolean,
    hasHiddenSeenEntries: Boolean,
    readMessageIds: List<String>,
    hasReadEntries: Boolean,
    popoverRef: Ref<HTMLDivElement>,
    onToggleSeenVisibility: () -> Unit,
    onClose: () -> Unit,
    onScrollToEnd: () -> Unit,
) = div {
    ref = popoverRef
    className = ClassName("message-inbox")
    onScroll = { event ->
        val target = event.currentTarget
        if (hasMore && isScrolledNearBottom(target)) {
            onScrollToEnd()
        }
    }
    div {
        className = ClassName("message-inbox__header")
        span {
            className = ClassName("message-inbox__title")
            +translator.translate("connected.messages.inbox.title")
        }
        button {
            className =
                if (showSeenMessages) {
                    ClassName("message-inbox__filter message-inbox__filter--active")
                } else {
                    ClassName("message-inbox__filter")
                }
            ariaLabel =
                translator.translate(
                    if (showSeenMessages) {
                        "connected.messages.inbox.hideRead"
                    } else {
                        "connected.messages.inbox.showRead"
                    },
                )
            onClick = { onToggleSeenVisibility() }
            +translator.translate(
                if (showSeenMessages) {
                    "connected.messages.inbox.hideRead"
                } else {
                    "connected.messages.inbox.showRead"
                },
            )
        }
        button {
            className = ClassName("message-inbox__close")
            ariaLabel = translator.translate("connected.messages.inbox.close")
            onClick = { onClose() }
            +"×"
        }
    }
    when {
        entries.isEmpty() && isLoading ->
            div {
                className = ClassName("message-inbox__empty")
                +translator.translate("connected.messages.inbox.loading")
            }
        entries.isEmpty() && errorText != null ->
            div {
                className = ClassName("message-inbox__error")
                +errorText
            }
        entries.isEmpty() ->
            div {
                className = ClassName("message-inbox__empty")
                +translator.translate(
                    if (!showSeenMessages && hasHiddenSeenEntries) {
                        "connected.messages.inbox.emptyUnread"
                    } else {
                        "connected.messages.inbox.empty"
                    },
                )
            }
        else -> {
            val readIds = readMessageIds.toSet()
            entries.forEach { entry ->
                val senderVisuals = identityVisualsFactory.createFromGid(entry.senderGid)
                val senderName = senderVisuals.name.toString().trim()
                div {
                    key = entry.id.unsafeCast<Key>()
                    className =
                        ClassName(
                            if (entry.id in readIds) {
                                "message-inbox__entry message-inbox__entry--read"
                            } else {
                                "message-inbox__entry"
                            },
                        )
                    div {
                        className = ClassName("message-inbox__sender")
                        img {
                            className = ClassName("message-inbox__sender-avatar")
                            src = senderVisuals.avatar
                            alt = senderName
                        }
                        div {
                            className = ClassName("message-inbox__sender-details")
                            span {
                                className = ClassName("message-inbox__sender-name")
                                +translator.translate(
                                    "connected.messages.inbox.from",
                                    mapOf("name" to senderName),
                                )
                            }
                            span {
                                className = ClassName("message-inbox__timestamp")
                                +formatTimestamp(entry.createdAt)
                            }
                        }
                    }
                    span {
                        className = ClassName("message-inbox__subject")
                        +entry.subject
                    }
                    if (showSeenMessages && hasReadEntries && entry.id in readIds) {
                        span {
                            className = ClassName("message-inbox__status")
                            +translator.translate("connected.messages.inbox.read")
                        }
                    }
                    div {
                        className = ClassName("message-inbox__body")
                        +entry.body
                    }
                }
            }
            if (errorText != null) {
                div {
                    className = ClassName("message-inbox__error")
                    +errorText
                }
            } else if (isLoading || isAppending) {
                div {
                    className = ClassName("message-inbox__meta")
                    +translator.translate(
                        if (isAppending) {
                            "connected.messages.inbox.loadingMore"
                        } else {
                            "connected.messages.inbox.loading"
                        },
                    )
                }
            }
        }
    }
}

private fun ChildrenBuilder.postComplaintQueuePopover(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    entries: List<ComplaintQueueEntry>,
    isLoading: Boolean,
    isAppending: Boolean,
    errorText: String?,
    hasMore: Boolean,
    showProcessedEntries: Boolean,
    hasHiddenProcessedEntries: Boolean,
    popoverRef: Ref<HTMLDivElement>,
    onToggleProcessedVisibility: () -> Unit,
    onClose: () -> Unit,
    onLoadMore: () -> Unit,
    decisionEntryId: String?,
    decisionTargetStatus: PostComplaintState?,
    decisionText: String,
    decisionErrorText: String?,
    isDecisionSubmitting: Boolean,
    onDecisionTextChange: (String) -> Unit,
    onCloseDecisionComposer: () -> Unit,
    onSubmitDecision: () -> Unit,
    onConfirm: (OpenPostComplaint) -> Unit,
    onReject: (OpenPostComplaint) -> Unit,
    onDelete: (OpenPostComplaint) -> Unit,
) = div {
    ref = popoverRef
    className = ClassName("message-inbox complaint-queue")
    div {
        className = ClassName("message-inbox__header")
        span {
            className = ClassName("message-inbox__title")
            +translator.translate("connected.complaints.admin.title")
        }
        button {
            className =
                if (showProcessedEntries) {
                    ClassName("message-inbox__filter message-inbox__filter--active")
                } else {
                    ClassName("message-inbox__filter")
                }
            ariaLabel =
                translator.translate(
                    if (showProcessedEntries) {
                        "connected.complaints.admin.hideProcessed"
                    } else {
                        "connected.complaints.admin.showProcessed"
                    },
                )
            onClick = { onToggleProcessedVisibility() }
            +translator.translate(
                if (showProcessedEntries) {
                    "connected.complaints.admin.hideProcessed"
                } else {
                    "connected.complaints.admin.showProcessed"
                },
            )
        }
        button {
            className = ClassName("message-inbox__close")
            ariaLabel = translator.translate("connected.complaints.admin.close")
            onClick = { onClose() }
            +"×"
        }
    }
    when {
        isLoading ->
            div {
                className = ClassName("message-inbox__empty")
                +translator.translate("connected.complaints.admin.loading")
            }

        errorText != null ->
            div {
                className = ClassName("message-inbox__error")
                +errorText
            }

        entries.isEmpty() ->
            div {
                className = ClassName("message-inbox__empty")
                +translator.translate(
                    if (!showProcessedEntries && hasHiddenProcessedEntries) {
                        "connected.complaints.admin.emptyOpen"
                    } else {
                        "connected.complaints.admin.empty"
                    },
                )
            }

        else -> {
            entries.forEach { entry ->
                val complaint = entry.complaint
                val complainantVisuals = identityVisualsFactory.createFromGid(complaint.complainantGid)
                val postAuthorVisuals = identityVisualsFactory.createFromGid(complaint.postAuthorGid)
                val showDecisionComposer = decisionEntryId == complaint.complaintId
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
                                +translator.translate(
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
                        +translator.translate(
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
                        +translator.translate(
                            "connected.complaints.admin.reason",
                            mapOf("reason" to complaint.reason),
                        )
                    }
                    if (entry.isOpen()) {
                        div {
                            className = ClassName("complaint-queue__actions")
                            button {
                                className = ClassName("button button--secondary complaint-queue__action")
                                disabled = isDecisionSubmitting
                                onClick = { onConfirm(complaint) }
                                +translator.translate("connected.complaints.admin.confirm")
                            }
                            button {
                                className = ClassName("button button--secondary complaint-queue__action")
                                disabled = isDecisionSubmitting
                                onClick = { onReject(complaint) }
                                +translator.translate("connected.complaints.admin.reject")
                            }
                            button {
                                className = ClassName("button button--primary complaint-queue__action")
                                disabled = isDecisionSubmitting
                                onClick = { onDelete(complaint) }
                                +translator.translate("connected.complaints.admin.delete")
                            }
                        }
                    }
                    if (showDecisionComposer) {
                        complaintComposerPopover(
                            translator = translator,
                            title = complaintDecisionTitle(translator, decisionTargetStatus),
                            placeholder = translator.translate("connected.complaints.admin.feedbackPlaceholder"),
                            text = decisionText,
                            isSubmitting = isDecisionSubmitting,
                            errorText = decisionErrorText,
                            sendLabel = translator.translate("connected.complaints.admin.send"),
                            onTextChange = onDecisionTextChange,
                            onSend = onSubmitDecision,
                            onClose = onCloseDecisionComposer,
                        )
                    }
                }
            }
            if (hasMore) {
                button {
                    className = ClassName("button button--secondary complaint-queue__load-more")
                    disabled = isAppending
                    onClick = { onLoadMore() }
                    +translator.translate("connected.complaints.admin.loadMore")
                }
            }
        }
    }
}

private fun complaintDecisionTitle(
    translator: Translator,
    targetStatus: PostComplaintState?,
): String =
    when (targetStatus) {
        PostComplaintState.CONFIRMED -> translator.translate("connected.complaints.admin.composeConfirmed")
        PostComplaintState.REJECTED -> translator.translate("connected.complaints.admin.composeRejected")
        PostComplaintState.DELETED -> translator.translate("connected.complaints.admin.composeDeleted")
        PostComplaintState.OPEN, null -> translator.translate("connected.complaints.admin.title")
    }

private fun OpenPostComplaint.toQueueEntry(): ComplaintQueueEntry = ComplaintQueueEntry(complaint = this, state = PostComplaintState.OPEN)

private val ComplaintQueueEntry.complaintId: String
    get() = complaint.complaintId

private fun ComplaintQueueEntry.isOpen(): Boolean = state == PostComplaintState.OPEN

private fun List<ComplaintQueueEntry>.filteredForComplaintQueue(showProcessedEntries: Boolean): List<ComplaintQueueEntry> {
    if (showProcessedEntries) {
        return this
    }
    return filter(ComplaintQueueEntry::isOpen)
}

private fun ChildrenBuilder.adminUserActionCardOverlay(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    recipientAvatar: String,
    recipientName: String,
    isComposerOpen: Boolean,
    subject: String,
    body: String,
    isSending: Boolean,
    errorText: String?,
    historyEntries: List<AdminMessage>,
    isHistoryLoading: Boolean,
    isHistoryAppending: Boolean,
    historyErrorText: String?,
    historyHasMore: Boolean,
    historyListRef: Ref<HTMLDivElement>,
    onOpenComposer: () -> Unit,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onHistoryScroll: () -> Unit,
) = div {
    className = ClassName("admin-user-card-overlay")
    onClick = { event: MouseEvent<*, *> ->
        if (event.target == event.currentTarget) {
            onClose()
        }
    }
    adminUserActionCard(
        translator = translator,
        identityVisualsFactory = identityVisualsFactory,
        recipientAvatar = recipientAvatar,
        recipientName = recipientName,
        isComposerOpen = isComposerOpen,
        subject = subject,
        body = body,
        isSending = isSending,
        errorText = errorText,
        historyEntries = historyEntries,
        isHistoryLoading = isHistoryLoading,
        isHistoryAppending = isHistoryAppending,
        historyErrorText = historyErrorText,
        historyHasMore = historyHasMore,
        historyListRef = historyListRef,
        onOpenComposer = onOpenComposer,
        onSubjectChange = onSubjectChange,
        onBodyChange = onBodyChange,
        onClose = onClose,
        onSend = onSend,
        onHistoryScroll = onHistoryScroll,
    )
}

private fun ChildrenBuilder.adminUserActionCard(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    recipientAvatar: String,
    recipientName: String,
    isComposerOpen: Boolean,
    subject: String,
    body: String,
    isSending: Boolean,
    errorText: String?,
    historyEntries: List<AdminMessage>,
    isHistoryLoading: Boolean,
    isHistoryAppending: Boolean,
    historyErrorText: String?,
    historyHasMore: Boolean,
    historyListRef: Ref<HTMLDivElement>,
    onOpenComposer: () -> Unit,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    onHistoryScroll: () -> Unit,
) = div {
    className = ClassName("admin-user-card")
    onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
    div {
        className = ClassName("admin-user-card__header")
        div {
            className = ClassName("admin-user-card__identity")
            img {
                className = ClassName("admin-user-card__avatar")
                src = recipientAvatar
                alt = recipientName
            }
            div {
                className = ClassName("admin-user-card__identity-text")
                span {
                    className = ClassName("admin-user-card__eyebrow")
                    +translator.translate("connected.messages.actions.cardEyebrow")
                }
                span {
                    className = ClassName("admin-user-card__name")
                    +recipientName
                }
            }
        }
        button {
            className = ClassName("admin-user-card__close")
            ariaLabel = translator.translate("connected.messages.actions.close")
            onClick = { onClose() }
            +"×"
        }
    }
    if (!isComposerOpen) {
        button {
            className = ClassName("button button--primary admin-user-card__primary-action")
            onClick = { onOpenComposer() }
            +translator.translate("connected.messages.actions.send")
        }
    }
    adminMessageHistorySection(
        translator = translator,
        identityVisualsFactory = identityVisualsFactory,
        historyEntries = historyEntries,
        isLoading = isHistoryLoading,
        isAppending = isHistoryAppending,
        errorText = historyErrorText,
        hasMore = historyHasMore,
        listRef = historyListRef,
        onScrollToEnd = onHistoryScroll,
    )
    if (isComposerOpen) {
        adminMessageComposePopover(
            translator = translator,
            recipientName = recipientName,
            subject = subject,
            body = body,
            isSending = isSending,
            errorText = errorText,
            onSubjectChange = onSubjectChange,
            onBodyChange = onBodyChange,
            onClose = onClose,
            onSend = onSend,
        )
    }
}

private fun ChildrenBuilder.adminMessageHistorySection(
    translator: Translator,
    identityVisualsFactory: IdentityVisualsFactory,
    historyEntries: List<AdminMessage>,
    isLoading: Boolean,
    isAppending: Boolean,
    errorText: String?,
    hasMore: Boolean,
    listRef: Ref<HTMLDivElement>,
    onScrollToEnd: () -> Unit,
) = div {
    className = ClassName("admin-user-card__history")
    div {
        className = ClassName("admin-user-card__history-header")
        span {
            className = ClassName("admin-user-card__history-title")
            +translator.translate("connected.messages.actions.history.title")
        }
    }
    div {
        ref = listRef
        className = ClassName("admin-user-card__history-list")
        onScroll = { event ->
            val target = event.currentTarget
            if (hasMore && isScrolledNearBottom(target)) {
                onScrollToEnd()
            }
        }
        when {
            isLoading ->
                div {
                    className = ClassName("admin-user-card__history-empty")
                    +translator.translate("connected.messages.actions.history.loading")
                }
            errorText != null ->
                div {
                    className = ClassName("admin-user-card__history-error")
                    +errorText
                }
            historyEntries.isEmpty() ->
                div {
                    className = ClassName("admin-user-card__history-empty")
                    +translator.translate("connected.messages.actions.history.empty")
                }
            else -> {
                historyEntries.forEach { entry ->
                    val senderVisuals = identityVisualsFactory.createFromGid(entry.senderGid)
                    val senderName = senderVisuals.name.toString().trim()
                    div {
                        key = entry.id.unsafeCast<Key>()
                        className = ClassName("admin-user-card__history-entry")
                        div {
                            className = ClassName("admin-user-card__history-meta")
                            div {
                                className = ClassName("admin-user-card__history-sender")
                                img {
                                    className = ClassName("admin-user-card__history-sender-avatar")
                                    src = senderVisuals.avatar
                                    alt = senderName
                                }
                                span {
                                    className = ClassName("admin-user-card__history-sender-name")
                                    +translator.translate(
                                        "connected.messages.inbox.from",
                                        mapOf("name" to senderName),
                                    )
                                }
                            }
                            span {
                                className = ClassName("admin-user-card__history-timestamp")
                                +formatTimestamp(entry.createdAt)
                            }
                        }
                        span {
                            className = ClassName("admin-user-card__history-subject")
                            +entry.subject
                        }
                        div {
                            className = ClassName("admin-user-card__history-body")
                            +entry.body
                        }
                        span {
                            className = ClassName("admin-user-card__history-lifetime")
                            +translator.translate(
                                "connected.messages.actions.history.lifetime",
                                mapOf("days" to entry.remainingLifetimeDays.toString()),
                            )
                        }
                    }
                }
                if (isAppending) {
                    div {
                        className = ClassName("admin-user-card__history-empty")
                        +translator.translate("connected.messages.actions.history.loadingMore")
                    }
                }
            }
        }
    }
}

private fun ChildrenBuilder.adminMessageComposePopover(
    translator: Translator,
    recipientName: String,
    subject: String,
    body: String,
    isSending: Boolean,
    errorText: String?,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
) = div {
    className = ClassName("message-compose")
    onClick = { event: MouseEvent<*, *> -> event.stopPropagation() }
    div {
        className = ClassName("message-compose__header")
        span {
            className = ClassName("message-compose__title")
            +translator.translate("connected.messages.compose.title", mapOf("name" to recipientName))
        }
        button {
            className = ClassName("message-compose__close")
            ariaLabel = translator.translate("connected.messages.compose.close")
            onClick = { onClose() }
            +"×"
        }
    }
    input {
        className = ClassName("message-compose__subject")
        placeholder = translator.translate("connected.messages.compose.subject")
        value = subject
        disabled = isSending
        onChange = { event: ChangeEvent<HTMLInputElement, HTMLInputElement> ->
            onSubjectChange(event.currentTarget.value)
        }
    }
    textarea {
        className = ClassName("message-compose__body")
        placeholder = translator.translate("connected.messages.compose.body")
        value = body
        disabled = isSending
        rows = 4
        onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
            onBodyChange(event.currentTarget.value)
        }
    }
    if (errorText != null) {
        div {
            className = ClassName("message-compose__error")
            +errorText
        }
    }
    div {
        className = ClassName("message-compose__actions")
        button {
            className = ClassName("button button--secondary message-compose__cancel")
            disabled = isSending
            onClick = { onClose() }
            +translator.translate("connected.messages.compose.cancel")
        }
        button {
            className = ClassName("button button--primary message-compose__send")
            disabled = subject.isBlank() || body.isBlank() || isSending
            onClick = { onSend() }
            +translator.translate("connected.messages.compose.send")
        }
    }
}

private fun ChildrenBuilder.floatingComposerButton(
    label: String,
    onActivate: () -> Unit,
) = button {
    className = ClassName("composer-button")
    ariaLabel = label
    title = label
    onClick = { onActivate() }
    +"+"
}

private fun ChildrenBuilder.composerDialog(
    translator: Translator,
    text: String,
    onTextChange: (String) -> Unit,
    onClose: () -> Unit,
    onSend: () -> Unit,
    isPublishing: Boolean,
    errorText: String?,
) = div {
    className = ClassName("composer-overlay")
    div {
        className = ClassName("composer-dialog")
        div {
            className = ClassName("composer-dialog__header")
            span {
                className = ClassName("composer-dialog__title")
                +translator.translate("connected.composer.title")
            }
            button {
                className = ClassName("composer-dialog__close")
                ariaLabel = translator.translate("connected.composer.close")
                onClick = { onClose() }
                +"x"
            }
        }
        div {
            className = ClassName("composer-dialog__body")
            textarea {
                className = ClassName("composer-dialog__textarea")
                placeholder = translator.translate("connected.composer.placeholder")
                value = text
                disabled = isPublishing
                rows = 5
                onChange = { event: ChangeEvent<HTMLTextAreaElement, HTMLTextAreaElement> ->
                    onTextChange(event.currentTarget.value)
                }
            }
            if (errorText != null) {
                div {
                    className = ClassName("composer-dialog__error")
                    +errorText
                }
            }
        }
        div {
            className = ClassName("composer-dialog__footer")
            button {
                className = ClassName("button button--primary composer-dialog__send")
                disabled = text.trim().isEmpty() || isPublishing
                onClick = { onSend() }
                +translator.translate("connected.composer.send")
            }
        }
    }
}

private object ConnectedViewInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

private fun isScrolledNearBottom(element: HTMLDivElement): Boolean {
    val thresholdPx = 24
    return element.scrollTop + element.clientHeight >= element.scrollHeight - thresholdPx
}

private fun formatTimestamp(epochSeconds: Long): String =
    Date(epochSeconds.toDouble() * 1000).toLocaleString()

private fun formatSimilarity(similarity: Float): String =
    (similarity.toDouble().asDynamic().toFixed(3) as String)
