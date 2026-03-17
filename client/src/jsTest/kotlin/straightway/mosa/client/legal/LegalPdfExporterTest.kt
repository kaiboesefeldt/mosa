package straightway.mosa.client.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement

class LegalPdfExporterTest {
    @Test
    fun `continuation header logo size keeps image aspect ratio`() {
        assertEquals(
            PdfImageSize(width = 26.0, height = 13.0),
            calculateLogoSizeForHeader(maxHeight = 13.0, aspectRatio = 2.0),
        )
    }

    @Test
    fun `right aligned footer text x keeps right margin`() {
        assertEquals(
            402.0,
            calculateRightAlignedTextX(
                pageWidth = 500.0,
                margin = 28.0,
                textWidth = 70.0,
            ),
        )
    }

    @Test
    fun `footer page counter includes current and total page`() {
        assertEquals(
            "Seite 2 von 5",
            buildPdfPageCounterText(pageNumber = 2, totalPages = 5),
        )
    }

    @Test
    fun `footer export label prefixes date`() {
        assertEquals("Export: 2026-03-06", buildPdfExportDateLabel("2026-03-06"))
    }

    @Test
    fun `continuation page header text prefixes document name with mosa`() {
        assertEquals("Mosa - Datenschutzerklaerung", buildContinuationPageHeaderText("Datenschutzerklaerung"))
    }

    @Test
    fun `page break chooses low ink row near target instead of cutting through dense rows`() {
        val rowInk =
            IntArray(160) { index ->
                when (index) {
                    in 80..95 -> 220
                    103 -> 0
                    else -> 8
                }
            }

        val endExclusive =
            choosePageSliceEndExclusive(
                rowInk = rowInk,
                startInclusive = 0,
                idealEndExclusive = 100,
                minSliceHeightPx = 60,
                searchWindowPx = 10,
            )

        assertEquals(101, endExclusive)
    }

    @Test
    fun `page break avoids isolated low row when neighboring rows are dense`() {
        val rowInk =
            IntArray(180) { 8 }.also {
                for (index in 80..95) {
                    it[index] = 220
                }
                it[102] = 220
                it[103] = 0
                it[104] = 220
                it[105] = 220
                for (index in 108..112) {
                    it[index] = 1
                }
            }

        val endExclusive =
            choosePageSliceEndExclusive(
                rowInk = rowInk,
                startInclusive = 0,
                idealEndExclusive = 100,
                minSliceHeightPx = 60,
                searchWindowPx = 20,
            )

        assertEquals(110, endExclusive)
    }

    @Test
    fun `clone for pdf removes comparison boxes and dotted underline class`() {
        val content = document.createElement("div") as HTMLDivElement
        content.innerHTML =
            """
            <h1>Datenschutzerklaerung</h1>
            <p><span class="legal-ct-bg-yellow legal-ct-baseline-diff">Stand: 2026-03-05</span></p>
            <div class="legal-ct-comparison-box">
              <div class="legal-ct-comparison-box__label">Original</div>
              <div class="legal-ct-comparison-box__text">Dieser Text darf nicht im PDF erscheinen.</div>
            </div>
            """.trimIndent()

        val clone = cloneLegalContentForPdf(content)
        val text = extractLegalPdfText(content)

        assertFalse(clone.querySelectorAll(".legal-ct-comparison-box").length > 0)
        assertFalse(clone.querySelectorAll(".legal-ct-baseline-diff").length > 0)
        assertTrue(text.contains("Datenschutzerklaerung"))
        assertTrue(text.contains("Stand: 2026-03-05"))
        assertFalse(text.contains("Dieser Text darf nicht im PDF erscheinen."))
    }

    @Test
    fun `build export root includes logo and keeps semantic html structure`() {
        val content = document.createElement("div") as HTMLDivElement
        content.innerHTML =
            """
            <h1>Datenschutzerklaerung</h1>
            <p>Stand: 2026-03-05</p>
            <ul><li>Punkt 1</li><li>Punkt 2</li></ul>
            """.trimIndent()

        val exportRoot =
            buildLegalPdfExportRoot(
                source = content,
                logoUrl = "data:image/png;base64,ZmFrZQ==",
                firstPageTitle = "Mosa",
            )

        val logo = exportRoot.querySelector(".legal-pdf-export__logo")
        val title = exportRoot.querySelector(".legal-pdf-export__title")
        val titleText = exportRoot.querySelector(".legal-pdf-export__title-text")
        val heading = exportRoot.querySelector("h1")
        val listItems = exportRoot.querySelectorAll("li")

        assertTrue(logo != null)
        assertTrue(title != null)
        assertTrue(titleText != null)
        assertEquals("Mosa", titleText.textContent)
        assertEquals("flex-start", title.asDynamic().style.justifyContent as String)
        assertTrue((logo.asDynamic().getAttribute("src") as String).contains("data:image/png;base64,ZmFrZQ=="))
        assertTrue(heading != null)
        assertTrue(listItems.length == 2)
    }
}
