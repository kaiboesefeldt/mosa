package straightway.mosa.client.ui

import kotlinx.coroutines.async
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.identity.PasswordPromptController
import straightway.mosa.client.identity.PasswordPromptKind
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.userEvent

@OptIn(ExperimentalWasmJsInterop::class)
class PasswordPromptOverlayTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `returns entered password when create prompt is confirmed`() = runTest {
        AppModule.start()
        val promptResult = async { PasswordPromptController.prompt(PasswordPromptKind.Create) }
        val view = render(PasswordPromptOverlay.create())
        val user = userEvent.setup()

        eventually {
            check(view.container.querySelectorAll(".password-popover__field input").length == 2)
        }

        val inputs = view.container.querySelectorAll(".password-popover__field input")
        user.type(inputs.item(0) ?: error("Password input missing"), "secret-pass").await()
        user.type(inputs.item(1) ?: error("Confirmation input missing"), "secret-pass").await()
        user.click(view.container.querySelector(".password-popover__actions .button--primary") ?: error("Confirm button missing")).await()

        assertEquals("secret-pass", promptResult.await())
    }

    private suspend fun eventually(assertion: () -> Unit) {
        repeat(100) {
            runCatching {
                assertion()
                return
            }
            delay(10)
        }
        assertion()
    }
}
