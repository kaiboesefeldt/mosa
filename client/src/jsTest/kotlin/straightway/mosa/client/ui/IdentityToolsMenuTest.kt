package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import org.koin.core.component.get
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.screen
import straightway.mosa.client.testing.userEvent

class IdentityToolsMenuTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `collapses tools menu by default`() = runTest {
        renderIdentityProfileView()

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val container = toggleButton.parentElement!!

        assertFalse(container.classList.contains("profile-dialog__identity-tools--expanded"))
    }

    @Test
    fun `expands tools menu after toggle`() = runTest {
        renderIdentityProfileView()

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val user = userEvent.setup()
        user.click(toggleButton).await()
        val container = toggleButton.parentElement!!

        assertTrue(container.classList.contains("profile-dialog__identity-tools--expanded"))
        assertTrue(container.querySelector(".profile-dialog__identity-tools-menu") != null)
    }

    @Test
    fun `closes tools menu after action trigger`() = runTest {
        var handled = false
        renderIdentityProfileView {
            onDelete = { handled = true }
        }

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val user = userEvent.setup()
        user.click(toggleButton).await()
        val deleteButton = screen.getByRole("button", json("name" to text("button.identity.delete.aria")))
        user.click(deleteButton).await()
        val container = toggleButton.parentElement!!

        assertTrue(handled)
        assertFalse(container.classList.contains("profile-dialog__identity-tools--expanded"))
    }

    @Test
    fun `disables tool actions while busy`() = runTest {
        var handled = false
        renderIdentityProfileView {
            isBusy = true
            canDelete = true
            onDelete = { handled = true }
        }

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val user = userEvent.setup()
        user.click(toggleButton).await()
        val deleteButton = screen.getByRole("button", json("name" to text("button.identity.delete.aria")))
        val isDisabled = deleteButton.asDynamic().disabled as Boolean
        user.click(deleteButton).await()

        assertTrue(isDisabled)
        assertFalse(handled)
    }

    @Test
    fun `collapses tools menu after outside pointer interaction`() = runTest {
        renderIdentityProfileView()

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val user = userEvent.setup()
        user.click(toggleButton).await()
        val overlay = document.querySelector(".profile-overlay")
        user.click(overlay).await()
        val container = toggleButton.parentElement!!

        assertFalse(container.classList.contains("profile-dialog__identity-tools--expanded"))
    }

    @Test
    fun `keeps disabled actions non interactive`() = runTest {
        var handled = false
        renderIdentityProfileView {
            canDownload = false
            onDownload = { handled = true }
        }

        val toggleButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        val user = userEvent.setup()
        user.click(toggleButton).await()
        val downloadButton = screen.getByRole("button", json("name" to text("button.identity.download.aria")))
        val isDisabled = downloadButton.asDynamic().disabled as Boolean
        user.click(downloadButton).await()

        assertTrue(isDisabled)
        assertFalse(handled)
    }

    private fun text(key: String): String = AppModule.get<Translator>().translate(key)
}
