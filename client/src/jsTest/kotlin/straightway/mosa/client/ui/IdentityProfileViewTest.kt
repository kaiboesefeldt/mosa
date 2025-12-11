package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import org.koin.core.component.get
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.screen
import straightway.mosa.client.testing.userEvent

class IdentityProfileViewTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `keeps connect avatar enabled when busy`() = runTest {
        renderIdentityProfileView {
            isBusy = true
        }

        val connectButton = screen.getByRole("button", json("name" to text("profile.identityAvatar.connect")))

        assertFalse(connectButton.asDynamic().disabled as Boolean)
    }

    @Test
    fun `ignores connect request when busy`() = runTest {
        var handled = false
        renderIdentityProfileView {
            isBusy = true
            onConnect = { handled = true }
        }

        val connectButton = screen.getByRole("button", json("name" to text("profile.identityAvatar.connect")))

        val user = userEvent.setup()
        user.click(connectButton).await()

        assertFalse(handled)
    }

    @Test
    fun `invokes connect handler when interaction finishes`() = runTest {
        var handled = false
        renderIdentityProfileView {
            canConnect = true
            onConnect = { handled = true }
        }

        val connectButton = screen.getByRole("button", json("name" to text("profile.identityAvatar.connect")))

        val user = userEvent.setup()
        user.click(connectButton).await()

        assertTrue(handled)
    }

    @Test
    fun `invokes next handler when user navigates forward`() = runTest {
        var handled = false
        renderIdentityProfileView {
            canNavigateNext = true
            onNext = { handled = true }
        }

        val nextButton = screen.getByRole("button", json("name" to text("button.identity.next.aria")))

        val user = userEvent.setup()
        user.click(nextButton).await()

        assertTrue(handled)
    }

    @Test
    fun `shows plus icon when last identity is active`() = runTest {
        renderIdentityProfileView {
            canNavigateNext = true
            isShowingLastIdentity = true
        }

        val nextButton = screen.getByRole("button", json("name" to text("button.identity.next.aria")))

        val plusIcon = nextButton.querySelector("i.fa-user-plus")

        assertTrue(plusIcon != null)
    }

    @Test
    fun `invokes delete handler when user removes identity`() = runTest {
        var handled = false
        renderIdentityProfileView {
            canDelete = true
            onDelete = { handled = true }
        }

        val user = userEvent.setup()
        val toolsButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        user.click(toolsButton).await()
        val deleteButton = screen.getByRole("button", json("name" to text("button.identity.delete.aria")))
        user.click(deleteButton).await()

        assertTrue(handled)
    }

    @Test
    fun `invokes upload handler when user requests identity upload`() = runTest {
        var handled = false
        renderIdentityProfileView {
            canUpload = true
            onUpload = { handled = true }
        }

        val user = userEvent.setup()
        val toolsButton = screen.getByRole("button", json("name" to text("button.identity.tools.aria")))
        user.click(toolsButton).await()
        val uploadButton = screen.getByRole("button", json("name" to text("button.identity.upload.aria")))
        user.click(uploadButton).await()

        assertTrue(handled)
    }

    private fun text(key: String): String = AppModule.get<Translator>().translate(key)
}
