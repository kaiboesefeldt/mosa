package straightway.mosa.client.ui.identityconnected.moderation

import kotlinx.coroutines.launch
import react.RefObject
import react.useRef
import react.useState
import straightway.mosa.client.content.AdminMessageInboxService
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.client.ui.AdminMessageInboxStateSnapshot
import straightway.mosa.client.ui.AdminMessageInboxStateStore
import straightway.mosa.client.ui.mergeFetchedEntries
import straightway.mosa.client.ui.nextRefreshFromTime
import straightway.mosa.client.ui.identityconnected.common.ConnectedViewFeedbackContext
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult
import web.html.HTMLButtonElement
import web.html.HTMLDivElement

internal data class ModerationInboxController(
    val state: ModerationInboxControllerState,
    val actions: ModerationInboxControllerActions,
    val refs: ModerationInboxControllerRefs,
)

internal data class ModerationInboxControllerState(
    val isOpen: Boolean,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val entries: List<AdminMessage>,
    val seenMessageIds: List<String>,
    val showSeenEntries: Boolean,
    val hiddenSeenMessageIds: List<String>,
    val error: String?,
    val page: Int,
    val hasMore: Boolean,
    val refreshFromTime: Long,
    val unreadCount: Int,
)

internal data class ModerationInboxControllerActions(
    val toggleSeenVisibility: () -> Unit,
    val loadInbox: () -> Unit,
    val closeInbox: () -> Unit,
    val loadMoreInbox: () -> Unit,
    val refreshInbox: (Boolean) -> Unit,
)

internal data class ModerationInboxControllerRefs(
    val isOpenRef: RefObject<Boolean>,
    val buttonRef: RefObject<HTMLButtonElement>,
    val popoverRef: RefObject<HTMLDivElement>,
)

internal data class ModerationInboxControllerDependencies(
    val context: ConnectedViewFeedbackContext,
    val inboxStateStore: AdminMessageInboxStateStore,
    val adminMessageInboxService: AdminMessageInboxService,
    val podBaseProvider: PodBaseProvider,
    val connectedIdentityGid: String?,
)

internal fun useModerationInboxController(
    dependencies: ModerationInboxControllerDependencies,
): ModerationInboxController {
    var isOpen by useState(false)
    var isLoading by useState(false)
    var isAppending by useState(false)
    var entries by useState<List<AdminMessage>>(emptyList())
    var seenMessageIds by useState<List<String>>(emptyList())
    var showSeenEntries by useState(false)
    var hiddenSeenMessageIds by useState<List<String>>(emptyList())
    var error by useState<String?>(null)
    var page by useState(1)
    var hasMore by useState(false)
    var refreshFromTime by useState(0L)
    var unreadCount by useState(0)
    val connectedIdentityGidRef = useRef(dependencies.connectedIdentityGid)
    val isOpenRef = useRef(false)
    val scrollRestoreRef = useRef<Double>()
    val buttonRef = useRef<HTMLButtonElement>()
    val popoverRef = useRef<HTMLDivElement>()
    isOpenRef.current = isOpen

    fun loadStateSnapshot(): AdminMessageInboxStateSnapshot =
        loadModerationInboxStateSnapshot(
            dependencies.inboxStateStore,
            dependencies.podBaseProvider,
            dependencies.connectedIdentityGid,
        )

    fun persistStateSnapshot(snapshot: AdminMessageInboxStateSnapshot) =
        persistModerationInboxStateSnapshot(
            dependencies.inboxStateStore,
            dependencies.podBaseProvider,
            dependencies.connectedIdentityGid,
            snapshot,
        )

    fun applyInboxPage(result: AdminMessageListResult, markAsSeen: Boolean, hiddenSeenIds: List<String>) {
        page = result.page
        hasMore = result.hasMore
        val nextSnapshot = loadStateSnapshot().mergeFetchedEntries(result.entries, markAsSeen)
        persistStateSnapshot(nextSnapshot)
        entries = nextSnapshot.entries
        seenMessageIds = nextSnapshot.seenMessageIds
        hiddenSeenMessageIds = hiddenSeenIds
        unreadCount = unreadModerationInboxCount(nextSnapshot)
    }

    fun refreshInbox(openAfterLoad: Boolean) {
        if (isLoading || isAppending) return
        val currentSnapshot = loadStateSnapshot()
        val nextRefreshFromTime = currentSnapshot.nextRefreshFromTime()
        val willBeOpen = openAfterLoad || isOpenRef.current == true
        val hiddenSeenIds =
            if (isOpenRef.current == true) hiddenSeenMessageIds else currentSnapshot.seenMessageIds
        isOpen = willBeOpen
        isLoading = true
        isAppending = false
        refreshFromTime = nextRefreshFromTime
        error = null
        dependencies.context.async.scope.launch {
            val result =
                runCatching {
                    dependencies.adminMessageInboxService.fetch(page = 1, fromTime = nextRefreshFromTime)
                }.getOrNull()
            isLoading = false
            if (result == null) {
                entries = currentSnapshot.entries
                seenMessageIds = currentSnapshot.seenMessageIds
                hiddenSeenMessageIds = hiddenSeenIds
                error = dependencies.context.async.translator.translate("connected.messages.inbox.error")
            } else {
                applyInboxPage(result, willBeOpen, hiddenSeenIds)
            }
        }
    }

    val actions =
        buildModerationInboxActions(
            dependencies =
                ModerationInboxActionDependencies(
                    context = dependencies.context,
                    adminMessageInboxService = dependencies.adminMessageInboxService,
                ),
            inbox =
                ModerationInboxModel(
                    refreshInbox = ::refreshInbox,
                    isOpen = isOpen,
                    isLoading = isLoading,
                    isAppending = isAppending,
                    hasMore = hasMore,
                    page = page,
                    refreshFromTime = refreshFromTime,
                    hiddenSeenMessageIds = hiddenSeenMessageIds,
                    setInboxOpen = { isOpen = it },
                    setInboxError = { error = it },
                    setInboxAppending = { isAppending = it },
                    applyInboxPage = ::applyInboxPage,
                    rememberInboxScrollTop = { scrollRestoreRef.current = popoverRef.current?.scrollTop },
                ),
        )

    useModerationInboxLifecycle(
        connectedIdentityGid = dependencies.connectedIdentityGid,
        currentConnectedIdentityGid = connectedIdentityGidRef.current,
        setConnectedIdentityGid = { connectedIdentityGidRef.current = it },
        loadInboxStateSnapshot = ::loadStateSnapshot,
        refreshInbox = ::refreshInbox,
        inboxEntries = entries,
        isInboxAppending = isAppending,
        inboxPopoverRef = popoverRef,
        inboxScrollRestoreRef = scrollRestoreRef,
        setInboxEntries = { entries = it },
        setSeenInboxMessageIds = { seenMessageIds = it },
        setHiddenSeenInboxMessageIds = { hiddenSeenMessageIds = it },
        setShowSeenInboxEntries = { showSeenEntries = it },
        setUnreadInboxCount = { unreadCount = it },
        setInboxError = { error = it },
        setInboxLoading = { isLoading = it },
        setInboxOpen = { isOpen = it },
    )

    return ModerationInboxController(
        state =
            ModerationInboxControllerState(
                isOpen = isOpen,
                isLoading = isLoading,
                isAppending = isAppending,
                entries = entries,
                seenMessageIds = seenMessageIds,
                showSeenEntries = showSeenEntries,
                hiddenSeenMessageIds = hiddenSeenMessageIds,
                error = error,
                page = page,
                hasMore = hasMore,
                refreshFromTime = refreshFromTime,
                unreadCount = unreadCount,
            ),
        actions =
            ModerationInboxControllerActions(
                toggleSeenVisibility = { showSeenEntries = !showSeenEntries },
                loadInbox = actions.loadInbox,
                closeInbox = actions.closeInbox,
                loadMoreInbox = actions.loadMoreInbox,
                refreshInbox = ::refreshInbox,
            ),
        refs =
            ModerationInboxControllerRefs(
                isOpenRef = isOpenRef,
                buttonRef = buttonRef,
                popoverRef = popoverRef,
            ),
    )
}
