package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import org.w3c.dom.events.Event
import react.useEffect
import react.useState

internal enum class MobilePane {
    Topics,
    Posts,
    Replies,
}

internal data class IdentityConnectedViewViewportState(
    val isMobile: Boolean,
    val isTouchDevice: Boolean,
    val mobilePane: MobilePane,
)

internal data class IdentityConnectedViewViewportActions(
    val setMobilePane: (MobilePane) -> Unit,
)

internal data class IdentityConnectedViewViewportController(
    val state: IdentityConnectedViewViewportState,
    val actions: IdentityConnectedViewViewportActions,
)

internal fun useIdentityConnectedViewViewportController(): IdentityConnectedViewViewportController {
    var isMobile by useState(false)
    var isTouchDevice by useState(false)
    var mobilePane by useState(MobilePane.Topics)

    useEffect(*emptyArray<dynamic>()) {
        fun updateIsMobile() {
            if (window.innerWidth <= 900) {
                isMobile = true
            } else {
                isMobile = false
                mobilePane = MobilePane.Topics
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

    useEffect(*emptyArray<dynamic>()) {
        val coarsePointer = window.matchMedia("(pointer: coarse)").matches
        val hasTouch = js("('ontouchstart' in window)") as Boolean
        isTouchDevice = coarsePointer || hasTouch
    }

    return IdentityConnectedViewViewportController(
        state =
            IdentityConnectedViewViewportState(
                isMobile = isMobile,
                isTouchDevice = isTouchDevice,
                mobilePane = mobilePane,
            ),
        actions = IdentityConnectedViewViewportActions(setMobilePane = { mobilePane = it }),
    )
}
