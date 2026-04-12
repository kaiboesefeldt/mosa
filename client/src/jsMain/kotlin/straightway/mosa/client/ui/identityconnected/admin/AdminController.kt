package straightway.mosa.client.ui.identityconnected.admin

import straightway.mosa.client.ui.identityconnected.common.*

import straightway.mosa.client.ui.*

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlin.js.unsafeCast
import org.w3c.dom.events.Event
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.client.content.AdminMessageHistoryService
import straightway.mosa.client.content.AdminMessagePublisher
import straightway.mosa.client.i18n.Translator
import straightway.mosa.message.AdminMessage
import web.dom.Element
import web.html.HTMLDivElement

internal fun useAdminController(
    dependencies: AdminActionDependencies,
): AdminController {
    var adminUserCardAnchorId by useState<String?>(null)
    var adminMessageRecipientGid by useState<String?>(null)
    var adminMessageRecipientName by useState<String?>(null)
    var adminMessageRecipientAvatar by useState<String?>(null)
    var isRecipientBlocked by useState(false)
    var isRecipientAdmin by useState(false)
    var isRecipientCapabilitiesLoading by useState(false)
    var isModerationRunning by useState(false)
    var moderationError by useState<String?>(null)
    var adminMessageSubject by useState("")
    var adminMessageBody by useState("")
    var isAdminMessageSending by useState(false)
    var adminMessageError by useState<String?>(null)
    var isAdminMessageComposerOpen by useState(false)
    var adminHistoryEntries by useState<List<AdminMessage>>(emptyList())
    var isAdminHistoryLoading by useState(false)
    var isAdminHistoryAppending by useState(false)
    var adminHistoryError by useState<String?>(null)
    var adminHistoryPage by useState(1)
    var adminHistoryHasMore by useState(false)
    val adminUserCardAnchorIdRef = useRef("")
    val adminHistoryListRef = useRef<HTMLDivElement>()
    val adminHistoryScrollRestoreRef = useRef<Double>()
    adminUserCardAnchorIdRef.current = adminUserCardAnchorId ?: ""

    val actions =
        buildAdminActions(
            dependencies = dependencies,
            recipient =
                AdminRecipientModel(
                    anchorId = adminUserCardAnchorId,
                    recipientGid = adminMessageRecipientGid,
                    setAnchorId = { adminUserCardAnchorId = it },
                    setRecipientGid = { adminMessageRecipientGid = it },
                    setRecipientName = { adminMessageRecipientName = it },
                    setRecipientAvatar = { adminMessageRecipientAvatar = it },
                    isBlocked = isRecipientBlocked,
                    isAdmin = isRecipientAdmin,
                    isLoadingCapabilities = isRecipientCapabilitiesLoading,
                    isModerationRunning = isModerationRunning,
                    moderationError = moderationError,
                    setBlocked = { isRecipientBlocked = it },
                    setAdmin = { isRecipientAdmin = it },
                    setLoadingCapabilities = { isRecipientCapabilitiesLoading = it },
                    setModerationRunning = { isModerationRunning = it },
                    setModerationError = { moderationError = it },
                ),
            composer =
                AdminComposerModel(
                    subject = adminMessageSubject,
                    body = adminMessageBody,
                    isSending = isAdminMessageSending,
                    setSubject = { adminMessageSubject = it },
                    setBody = { adminMessageBody = it },
                    setSending = { isAdminMessageSending = it },
                    setError = { adminMessageError = it },
                    setOpen = { isAdminMessageComposerOpen = it },
                ),
            history =
                AdminHistoryModel(
                    entries = adminHistoryEntries,
                    isLoading = isAdminHistoryLoading,
                    isAppending = isAdminHistoryAppending,
                    hasMore = adminHistoryHasMore,
                    page = adminHistoryPage,
                    setEntries = { adminHistoryEntries = it },
                    setLoading = { isAdminHistoryLoading = it },
                    setAppending = { isAdminHistoryAppending = it },
                    setError = { adminHistoryError = it },
                    setPage = { adminHistoryPage = it },
                    setHasMore = { adminHistoryHasMore = it },
                    rememberAdminHistoryScrollTop = { adminHistoryScrollRestoreRef.current = adminHistoryListRef.current?.scrollTop },
                ),
        )

    useEffect(arrayOf(adminHistoryEntries, isAdminHistoryAppending)) {
        val scrollTop = adminHistoryScrollRestoreRef.current ?: return@useEffect
        adminHistoryListRef.current?.scrollTop = scrollTop
        if (!isAdminHistoryAppending) {
            adminHistoryScrollRestoreRef.current = null
        }
    }

    useEffect(*emptyArray<dynamic>()) {
        val adminUserCardOutsideClickListener: (Event) -> Unit = listener@{ event ->
            if ((adminUserCardAnchorIdRef.current ?: "").isEmpty()) return@listener
            val target = event.target?.unsafeCast<Element?>() ?: return@listener
            val clickedTrigger = target.asDynamic().closest(".admin-user-trigger") != null
            val clickedCard = target.asDynamic().closest(".admin-user-card") != null
            if (!clickedTrigger && !clickedCard) {
                actions.closeAdminUserCard()
            }
        }
        document.addEventListener("mousedown", adminUserCardOutsideClickListener)
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("mousedown", adminUserCardOutsideClickListener)
        }
    }

    return AdminController(
        state =
            AdminControllerState(
                adminUserCardAnchorId = adminUserCardAnchorId,
                adminMessageRecipientName = adminMessageRecipientName,
                adminMessageRecipientAvatar = adminMessageRecipientAvatar,
                isRecipientBlocked = isRecipientBlocked,
                isRecipientAdmin = isRecipientAdmin,
                isRecipientCapabilitiesLoading = isRecipientCapabilitiesLoading,
                isModerationRunning = isModerationRunning,
                moderationError = moderationError,
                adminMessageSubject = adminMessageSubject,
                adminMessageBody = adminMessageBody,
                isAdminMessageSending = isAdminMessageSending,
                adminMessageError = adminMessageError,
                isAdminMessageComposerOpen = isAdminMessageComposerOpen,
                adminHistoryEntries = adminHistoryEntries,
                isAdminHistoryLoading = isAdminHistoryLoading,
                isAdminHistoryAppending = isAdminHistoryAppending,
                adminHistoryError = adminHistoryError,
                adminHistoryHasMore = adminHistoryHasMore,
            ),
        refs = AdminControllerRefs(adminHistoryListRef = adminHistoryListRef),
        actions =
            AdminControllerActions(
                toggleAdminUserCard = actions.toggleAdminUserCard,
                openAdminMessageCompose = actions.openAdminMessageCompose,
                setAdminMessageSubject = actions.setAdminMessageSubject,
                setAdminMessageBody = actions.setAdminMessageBody,
                closeAdminUserCard = actions.closeAdminUserCard,
                sendAdminMessageToCurrentRecipient = actions.sendAdminMessageToCurrentRecipient,
                loadMoreAdminHistory = actions.loadMoreAdminHistory,
                blockCurrentRecipient = actions.blockCurrentRecipient,
                unblockCurrentRecipient = actions.unblockCurrentRecipient,
                deleteCurrentRecipient = actions.deleteCurrentRecipient,
            ),
    )
}
