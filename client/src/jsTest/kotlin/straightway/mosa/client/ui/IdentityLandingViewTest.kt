package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.json
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.fireEvent
import straightway.mosa.client.testing.screen
import straightway.mosa.client.testing.userEvent

class IdentityLandingViewTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `disabled connect button when busy`() = runTest {
        renderIdentityLandingView {
            isBusy = true
        }

        val connectButton = screen.getByRole("button", json("name" to "Connect"))

        assertTrue(connectButton.asDynamic().disabled as Boolean)
    }

    @Test
    fun `forwards selected pod changes`() = runTest {
        var selectedPod: String? = null
        renderIdentityLandingView {
            pods = listOf("pod-a", "pod-b")
            selectedPod = "pod-a"
            onSelectPod = { selectedPod = it }
        }

        val podSelect = screen.getByRole("combobox")

        fireEvent.change(
            podSelect,
            json(
                "target" to json("value" to "pod-b")
            )
        )

        assertEquals("pod-b", selectedPod)
    }

    @Test
    fun `invokes connect handler when interaction finishes`() = runTest {
        var handled = false
        renderIdentityLandingView {
            canConnect = true
            onConnect = { handled = true }
        }

        val connectButton = screen.getByRole("button", json("name" to "Connect"))

        val user = userEvent.setup()
        user.click(connectButton).await()

        assertTrue(handled)
    }

    @Test
    fun `invokes regeneration handler when user requests new identity`() = runTest {
        var handled = false
        renderIdentityLandingView {
            onGenerateNew = { handled = true }
        }

        val regenerateButton = screen.getByRole("button", json("name" to "Generate new identity"))

        val user = userEvent.setup()
        user.click(regenerateButton).await()

        assertTrue(handled)
    }
}
