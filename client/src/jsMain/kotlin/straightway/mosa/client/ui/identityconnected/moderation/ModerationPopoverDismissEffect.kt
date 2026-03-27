package straightway.mosa.client.ui.identityconnected.moderation

import kotlinx.browser.document
import kotlinx.coroutines.awaitCancellation
import kotlin.js.unsafeCast
import org.w3c.dom.events.Event
import react.RefObject
import react.useEffect
import web.dom.Element
import web.html.HTMLButtonElement
import web.html.HTMLDivElement

internal fun useModerationPopoverDismiss(
    isInboxOpenRef: RefObject<Boolean>,
    isComplaintQueueOpenRef: RefObject<Boolean>,
    inboxButtonRef: RefObject<HTMLButtonElement>,
    complaintQueueButtonRef: RefObject<HTMLButtonElement>,
    inboxPopoverRef: RefObject<HTMLDivElement>,
    complaintQueuePopoverRef: RefObject<HTMLDivElement>,
    closeInbox: () -> Unit,
    closeComplaintQueue: () -> Unit,
) {
    useEffect(*emptyArray<dynamic>()) {
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
        try {
            awaitCancellation()
        } finally {
            document.removeEventListener("mousedown", inboxOutsideClickListener)
            document.removeEventListener("mousedown", complaintQueueOutsideClickListener)
        }
    }
}
