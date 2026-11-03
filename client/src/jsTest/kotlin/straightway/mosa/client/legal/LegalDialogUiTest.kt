@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package straightway.mosa.client.legal

import kotlin.js.json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.koin.core.component.get
import react.FC
import react.Props
import react.create
import react.useRef
import straightway.mosa.client.AppModule
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.testing.cleanup
import straightway.mosa.client.testing.render
import straightway.mosa.client.testing.screen
import web.html.HTMLDivElement

class LegalDialogUiTest {
    @AfterTest
    fun tearDown() {
        cleanup()
    }

    @Test
    fun `renders visible close x in legal dialog header`() = runTest {
        AppModule.start()

        render(LegalDialogOverlayHarness.create())

        val closeButton = screen.getByRole("button", json("name" to text("legal.close.label")))

        assertEquals("X", closeButton.textContent?.trim())
    }

    @Test
    fun `renders disabled pdf export action while terms are loading`() = runTest {
        AppModule.start()

        render(LegalDialogOverlayHarness.create())

        val exportButton = screen.getByRole("button", json("name" to text("legal.terms.downloadPdf.label")))

        assertTrue(exportButton.asDynamic().disabled as Boolean)
    }

    private fun text(key: String): String = AppModule.get<Translator>().translate(key)
}

private val LegalDialogOverlayHarness = FC<Props> {
    val contentRef = useRef<HTMLDivElement>()

    legalDialogOverlay(
        translator = AppModule.get(),
        activeLegalDocument = LEGAL_DOCUMENT_TERMS,
        legalDialogTitle = "Terms",
        legalDocumentState = LegalDialogState.Loading,
        isLegalPdfDownloading = false,
        legalDocumentContentRef = contentRef,
        legalDiffMarkers = emptyList(),
        legalViewportIndicator = LegalViewportIndicator(topPercent = 0.0, heightPercent = 100.0),
        onClose = {},
        onLegalPdfDownload = {},
        onComparisonToggle = {},
        onPrivacyScroll = {},
    )
}
