package straightway.mosa.client.ui

import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.get
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.screen

class IdentityDialogHeaderTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `renders localized landing header`() = runTest {
        val result = renderIdentityLandingView()

        val title = screen.getByRole("heading", json("name" to text("landing.title")))
        val description = result.container.querySelector(".landing-dialog__description")!!

        assertTrue(title.classList.contains("landing-dialog__title"))
        assertTrue(description.classList.contains("landing-dialog__description"))
        assertEquals(text("landing.description"), description.textContent)
    }

    private fun text(key: String): String = AppModule.get<Translator>().translate(key)
}
