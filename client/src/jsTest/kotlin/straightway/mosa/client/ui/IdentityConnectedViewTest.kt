package straightway.mosa.client.ui

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import react.create
import org.koin.core.component.get
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render

class IdentityConnectedViewTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `renders authentication token`() {
        val result =
            renderIdentityConnectedView {
                misJwt = "encoded-jwt"
                queryPreview = null
            }
        val content = result.container.textContent.orEmpty()

        assertTrue(content.contains(text("connected.authenticationTokenTitle")))
        assertTrue(content.contains("encoded-jwt"))
    }

    @Test
    fun `renders prototype preview when available`() {
        val preview = "nearest results"
        val result =
            renderIdentityConnectedView {
                queryPreview = preview
            }
        val content = result.container.textContent.orEmpty()

        assertTrue(content.contains(text("connected.prototypeQueryResultTitle")))
        assertTrue(content.contains(preview))
    }

    @Test
    fun `hides prototype preview when missing`() {
        val result =
            renderIdentityConnectedView {
                queryPreview = null
            }
        val content = result.container.textContent.orEmpty()

        assertFalse(content.contains(text("connected.prototypeQueryResultTitle")))
    }
}

private fun renderIdentityConnectedView(
    configure: IdentityConnectedViewProps.() -> Unit = {},
): RenderResult =
    render(
        IdentityConnectedView.create {
            AppModule.start()
            identityName = "Ada Lovelace"
            misJwt = "mis.jwt.token"
            queryPreview = "preview"
            configure()
        },
    )

private fun text(key: String): String = AppModule.get<Translator>().translate(key)
