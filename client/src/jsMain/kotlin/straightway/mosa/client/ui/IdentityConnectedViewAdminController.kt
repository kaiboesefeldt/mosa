package straightway.mosa.client.ui

import kotlinx.browser.document
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

internal fun useIdentityConnectedViewAdminController(
    translator: Translator,
    adminMessageHistoryService: AdminMessageHistoryService,
    adminMessagePublisher: AdminMessagePublisher,
    scope: CoroutineScope,
    canDeleteAsAdmin: Boolean,
    showToast: (String) -> Unit,
): IdentityConnectedViewAdminController {
    var adminUserCardAnchorId by useState<String?>(null)
    var adminMessageRecipientGid by useState<String?>(null)
    var adminMessageRecipientName by useState<String?>(null)
    var adminMessageRecipientAvatar by useState<String?>(null)
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
        buildIdentityConnectedViewAdminActions(
            translator = translator,
            adminMessageHistoryService = adminMessageHistoryService,
            adminMessagePublisher = adminMessagePublisher,
            scope = scope,
            canDeleteAsAdmin = { canDeleteAsAdmin },
            showToast = showToast,
            adminUserCardAnchorId = { adminUserCardAnchorId },
            adminMessageRecipientGid = { adminMessageRecipientGid },
            adminMessageSubject = { adminMessageSubject },
            adminMessageBody = { adminMessageBody },
            isAdminMessageSending = { isAdminMessageSending },
            isAdminHistoryLoading = { isAdminHistoryLoading },
            isAdminHistoryAppending = { isAdminHistoryAppending },
            adminHistoryHasMore = { adminHistoryHasMore },
            adminHistoryPage = { adminHistoryPage },
            adminHistoryEntries = { adminHistoryEntries },
            setAdminUserCardAnchorId = { adminUserCardAnchorId = it },
            setAdminMessageRecipientGid = { adminMessageRecipientGid = it },
            setAdminMessageRecipientName = { adminMessageRecipientName = it },
            setAdminMessageRecipientAvatar = { adminMessageRecipientAvatar = it },
            setAdminMessageSubjectState = { adminMessageSubject = it },
            setAdminMessageBodyState = { adminMessageBody = it },
            setAdminMessageSending = { isAdminMessageSending = it },
            setAdminMessageError = { adminMessageError = it },
            setAdminMessageComposerOpen = { isAdminMessageComposerOpen = it },
            setAdminHistoryEntries = { adminHistoryEntries = it },
            setAdminHistoryLoading = { isAdminHistoryLoading = it },
            setAdminHistoryAppending = { isAdminHistoryAppending = it },
            setAdminHistoryError = { adminHistoryError = it },
            setAdminHistoryPage = { adminHistoryPage = it },
            setAdminHistoryHasMore = { adminHistoryHasMore = it },
            rememberAdminHistoryScrollTop = { adminHistoryScrollRestoreRef.current = adminHistoryListRef.current?.scrollTop },
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

    return IdentityConnectedViewAdminController(
        adminUserCardAnchorId = adminUserCardAnchorId,
        adminMessageRecipientName = adminMessageRecipientName,
        adminMessageRecipientAvatar = adminMessageRecipientAvatar,
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
        adminHistoryListRef = adminHistoryListRef,
        toggleAdminUserCard = actions.toggleAdminUserCard,
        openAdminMessageCompose = actions.openAdminMessageCompose,
        setAdminMessageSubject = actions.setAdminMessageSubject,
        setAdminMessageBody = actions.setAdminMessageBody,
        closeAdminUserCard = actions.closeAdminUserCard,
        sendAdminMessageToCurrentRecipient = actions.sendAdminMessageToCurrentRecipient,
        loadMoreAdminHistory = actions.loadMoreAdminHistory,
    )
}
