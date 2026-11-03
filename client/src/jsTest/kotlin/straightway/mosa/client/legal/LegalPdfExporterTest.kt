package straightway.mosa.client.legal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.browser.document
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.js.unsafeCast
import org.w3c.dom.HTMLCanvasElement
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

    @Test
    fun `clear canvas resets width and height for cleanup`() {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 320
        canvas.height = 180

        clearCanvas(canvas)

        assertEquals(0, canvas.width)
        assertEquals(0, canvas.height)
    }

    @Test
    fun `save canvas as pdf clears temporary canvases when pdf export fails`() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 120
        canvas.height = 4

        val sliceCanvas = document.createElement("canvas") as HTMLCanvasElement
        val pdf = js("{}").unsafeCast<dynamic>()
        val page = js("{}").unsafeCast<dynamic>()
        pdf.embedFont = { _: dynamic -> Promise.resolve<dynamic>(js("{}")) }
        pdf.embedPng = { _: dynamic -> Promise.resolve<dynamic>(js("{}")) }
        pdf.addPage = { _: dynamic -> page }
        page.drawImage = { _: dynamic, _: dynamic ->
            throw IllegalStateException("boom")
        }
        page.drawText = { _: dynamic, _: dynamic -> Unit }
        page.drawLine = { _: dynamic -> Unit }
        pdf.save = { Promise.resolve<dynamic>(js("new Uint8Array()")) }

        try {
            saveCanvasAsPdf(
                canvas = canvas,
                fileName = "privacy-policy",
                logoUrl = "data:image/png;base64,ZmFrZQ==",
                documentName = "Datenschutzerklaerung",
                exportDateText = "2026-03-06",
                logoAspectRatio = 2.0,
                pdfDocumentFactory = { Promise.resolve<dynamic>(pdf) },
                sliceCanvasFactory = { sliceCanvas },
                pdfDownloader = { _, _ -> },
                rowInkCalculator = { IntArray(canvas.height) { 0 } },
                sliceImageDataProvider = { _, _, _ -> "data:image/png;base64,ZmFrZQ==" },
            )
        } catch (_: IllegalStateException) {
        }

        assertEquals(0, canvas.width)
        assertEquals(0, canvas.height)
        assertEquals(0, sliceCanvas.width)
        assertEquals(0, sliceCanvas.height)
    }

    @Test
    fun `save canvas as pdf preserves multipage images and downloads pdf`() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 120
        canvas.height = 600
        val sliceCanvas = document.createElement("canvas") as HTMLCanvasElement
        val pdf = js("{}").unsafeCast<dynamic>()
        val page = js("{}").unsafeCast<dynamic>()
        val font = js("{}").unsafeCast<dynamic>()
        var pageCount = 0
        var imageCount = 0
        var textCount = 0
        var lineCount = 0
        var downloadedFile: String? = null
        font.widthOfTextAtSize = { _: dynamic, _: dynamic -> 80.0 }
        pdf.embedFont = { _: dynamic -> Promise.resolve<dynamic>(font) }
        pdf.embedPng = { _: dynamic -> Promise.resolve<dynamic>(js("{}")) }
        pdf.addPage = { _: dynamic ->
            pageCount += 1
            page
        }
        pdf.save = { Promise.resolve<dynamic>(js("new Uint8Array()")) }
        page.drawImage = { _: dynamic, _: dynamic -> imageCount += 1 }
        page.drawText = { _: dynamic, _: dynamic -> textCount += 1 }
        page.drawLine = { _: dynamic -> lineCount += 1 }

        saveCanvasAsPdf(
            canvas = canvas,
            fileName = "privacy-policy.pdf",
            logoUrl = "data:image/png;base64,ZmFrZQ==",
            documentName = "Datenschutzerklaerung",
            exportDateText = "2026-03-06",
            logoAspectRatio = 2.0,
            pdfDocumentFactory = { Promise.resolve<dynamic>(pdf) },
            pdfDownloader = { _, name -> downloadedFile = name },
            sliceCanvasFactory = { sliceCanvas },
            rowInkCalculator = { IntArray(canvas.height) { 0 } },
            sliceImageDataProvider = { _, _, _ -> "data:image/png;base64,ZmFrZQ==" },
        )

        assertTrue(pageCount > 1)
        assertTrue(imageCount > pageCount)
        assertEquals(pageCount * 2 + pageCount - 1, textCount)
        assertEquals(pageCount * 2 - 1, lineCount)
        assertEquals("privacy-policy.pdf", downloadedFile)
        assertEquals(0, canvas.width)
        assertEquals(0, sliceCanvas.width)
    }

    @Test
    fun `save canvas as pdf creates a pdf with pdf lib`() = runTest {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        canvas.width = 120
        canvas.height = 40
        val logoCanvas = document.createElement("canvas") as HTMLCanvasElement
        logoCanvas.width = 2
        logoCanvas.height = 2
        val logoUrl = logoCanvas.toDataURL("image/png")
        var downloadedBytes: dynamic = null
        var downloadedFile: String? = null

        saveCanvasAsPdf(
            canvas = canvas,
            fileName = "privacy-policy",
            logoUrl = logoUrl,
            documentName = "Datenschutzerklaerung",
            exportDateText = "2026-03-06",
            logoAspectRatio = 1.0,
            pdfDownloader = { bytes, name ->
                downloadedBytes = bytes
                downloadedFile = name
            },
            sliceImageDataProvider = { _, sliceCanvas, _ -> sliceCanvas.toDataURL("image/png") },
        )

        assertTrue((downloadedBytes.length as Number).toInt() > 5)
        assertEquals(37, (downloadedBytes[0] as Number).toInt())
        assertEquals(80, (downloadedBytes[1] as Number).toInt())
        assertEquals(68, (downloadedBytes[2] as Number).toInt())
        assertEquals(70, (downloadedBytes[3] as Number).toInt())
        assertEquals(45, (downloadedBytes[4] as Number).toInt())
        assertEquals("privacy-policy.pdf", downloadedFile)
    }
}
