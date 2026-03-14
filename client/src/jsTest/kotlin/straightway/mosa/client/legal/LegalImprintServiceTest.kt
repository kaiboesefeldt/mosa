package straightway.mosa.client.legal

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalImprintServiceTest {
    @Test
    fun `loads imprint document from pod and renders html`() = runTest {
        val service =
            LegalImprintService(
                podBaseProvider = fixedImprintPodBaseProvider("https://pod.example"),
                podAccessFactory = fixedImprintPodAccessFactory(),
                imprintHtmlRenderer = ImprintHtmlRenderer(),
                podBaseResolver = { "https://pod.example" },
            )

        val document = service.loadImprintDocument()

        assertContains(document.html, "Example Pod GmbH")
        assertContains(document.html, "legal@example.invalid")
        assertEquals(emptyMap(), document.uncriticalTextByBlockId)
    }

    @Test
    fun `fails when no pod base exists`() = runTest {
        val service =
            LegalImprintService(
                podBaseProvider = fixedImprintPodBaseProvider(""),
                podAccessFactory = fixedImprintPodAccessFactory(),
                podBaseResolver = { null },
            )

        assertFailsWith<IllegalStateException> {
            service.loadImprintDocument()
        }
    }
}
