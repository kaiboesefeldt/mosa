package straightway.mosa.client.legal

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.math.abs
import kotlin.js.Date
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.CanvasRenderingContext2D

@JsModule("html2canvas")
@JsNonModule
private external fun html2canvas(
    element: HTMLDivElement,
    options: dynamic = definedExternally,
): Promise<HTMLCanvasElement>

@JsModule("jspdf")
@JsNonModule
private external val jspdfModule: dynamic

suspend fun downloadLegalPdf(
    source: HTMLDivElement,
    logoUrl: String,
    fileName: String,
) {
    val body = document.body ?: return
    val documentName = resolvePdfDocumentName(source)
    val exportDateText = resolvePdfExportDateText()
    val exportRoot =
        buildLegalPdfExportRoot(
            source = source,
            logoUrl = logoUrl,
            firstPageTitle = "Mosa",
        )
    exportRoot.style.position = "absolute"
    exportRoot.style.left = "0"
    exportRoot.style.top = "${window.scrollY + window.innerHeight + 200}px"
    exportRoot.style.width = "820px"
    exportRoot.style.background = "#ffffff"
    exportRoot.style.opacity = "1"
    exportRoot.style.setProperty("pointer-events", "none")
    exportRoot.style.zIndex = "0"
    body.appendChild(exportRoot)
    try {
        awaitNextFrame()
        awaitNextFrame()
        awaitImagesLoaded(exportRoot)
        val logoAspectRatio = resolveLogoAspectRatio(exportRoot)
        val canvasOptions: dynamic = js("({})")
        canvasOptions.scale = 2
        canvasOptions.useCORS = true
        canvasOptions.allowTaint = true
        canvasOptions.logging = false
        canvasOptions.backgroundColor = "#ffffff"
        canvasOptions.scrollX = 0
        canvasOptions.scrollY = 0
        canvasOptions.windowWidth = exportRoot.scrollWidth
        val canvas = html2canvas(exportRoot, canvasOptions).await()
        saveCanvasAsPdf(
            canvas = canvas,
            fileName = fileName,
            logoUrl = logoUrl,
            documentName = documentName,
            exportDateText = exportDateText,
            logoAspectRatio = logoAspectRatio,
        )
    } finally {
        exportRoot.remove()
    }
}

internal fun buildLegalPdfExportRoot(
    source: HTMLDivElement,
    logoUrl: String,
    firstPageTitle: String,
): HTMLDivElement {
    val root = document.createElement("div") as HTMLDivElement
    root.className = "legal-pdf-export"
    root.style.padding = "8px 10px 2px"
    root.style.color = "#111827"
    root.style.fontFamily = "Segoe UI, Roboto, Helvetica, Arial, sans-serif"

    val title = document.createElement("div") as HTMLDivElement
    title.className = "legal-pdf-export__title"
    title.style.display = "flex"
    title.style.alignItems = "center"
    title.style.justifyContent = "flex-start"
    title.style.setProperty("column-gap", "12px")
    title.style.margin = "0 0 18px 0"

    val logo = document.createElement("img") as HTMLImageElement
    logo.className = "legal-pdf-export__logo"
    logo.src = logoUrl
    logo.alt = "Mosa"
    logo.style.width = "120px"
    logo.style.height = "auto"
    logo.style.display = "block"
    title.appendChild(logo)
    val text = document.createElement("div") as HTMLDivElement
    text.className = "legal-pdf-export__title-text"
    text.textContent = firstPageTitle
    text.style.fontSize = "32px"
    text.style.fontWeight = "700"
    text.style.lineHeight = "1.1"
    title.appendChild(text)
    root.appendChild(title)
    root.appendChild(cloneLegalContentForPdf(source))
    return root
}

internal fun cloneLegalContentForPdf(source: HTMLDivElement): HTMLDivElement {
    val clone = source.cloneNode(deep = true) as HTMLDivElement
    val comparisonBoxes = clone.querySelectorAll(".legal-ct-comparison-box")
    for (index in comparisonBoxes.length - 1 downTo 0) {
        val node = comparisonBoxes.item(index) ?: continue
        node.parentNode?.removeChild(node)
    }
    val baselineMarkers = clone.querySelectorAll(".legal-ct-baseline-diff")
    for (index in 0 until baselineMarkers.length) {
        val node = baselineMarkers.item(index) ?: continue
        node.asDynamic().classList?.remove("legal-ct-baseline-diff")
    }
    return clone
}

internal fun extractLegalPdfText(source: HTMLDivElement): String {
    val clone = cloneLegalContentForPdf(source)
    val container = document.createElement("div") as HTMLDivElement
    container.style.position = "fixed"
    container.style.left = "-10000px"
    container.style.top = "-10000px"
    container.style.width = "800px"
    container.appendChild(clone)
    val body = document.body
    if (body != null) {
        body.appendChild(container)
    }
    val rawText =
        (clone.asDynamic().innerText as? String)
            ?: clone.textContent.orEmpty()
    if (body != null) {
        container.remove()
    }
    return rawText
        .replace("\r\n", "\n")
        .lines()
        .map { line -> line.trimEnd() }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private suspend fun awaitNextFrame() {
    suspendCoroutine { continuation ->
        window.requestAnimationFrame {
            continuation.resume(Unit)
        }
    }
}

private suspend fun awaitImagesLoaded(root: HTMLDivElement) {
    val images = root.getElementsByTagName("img")
    val pending = mutableListOf<Promise<Unit>>()
    for (index in 0 until images.length) {
        val image = images.item(index) as? HTMLImageElement ?: continue
        if (image.complete) continue
        val promise =
            Promise<Unit> { resolve, _ ->
                image.onload = { resolve(Unit) }
                image.onerror = { _, _, _, _, _ -> resolve(Unit) }
            }
        pending += promise
    }
    if (pending.isEmpty()) return
    Promise.all(pending.toTypedArray()).await()
}

private fun saveCanvasAsPdf(
    canvas: HTMLCanvasElement,
    fileName: String,
    logoUrl: String,
    documentName: String,
    exportDateText: String,
    logoAspectRatio: Double,
) {
    val pdfConfig: dynamic = js("({})")
    pdfConfig.orientation = "portrait"
    pdfConfig.unit = "pt"
    pdfConfig.format = "a4"
    val pdf = jspdfModule.jsPDF(pdfConfig)
    val pageWidth = pdf.internal.pageSize.getWidth() as Double
    val pageHeight = pdf.internal.pageSize.getHeight() as Double
    val margin = 28.0
    val continuationHeaderHeight = 22.0
    val continuationHeaderGap = 10.0
    val footerBandHeight = 22.0
    val targetWidth = pageWidth - (margin * 2.0)
    val pixelsPerPoint = canvas.width.toDouble() / targetWidth
    val firstPageTop = margin
    val continuationTop = margin + continuationHeaderHeight + continuationHeaderGap
    val contentBottom = margin + footerBandHeight
    val firstPageSlicePxTarget =
        ((pageHeight - firstPageTop - contentBottom) * pixelsPerPoint).toInt().coerceAtLeast(1)
    val continuationSlicePxTarget =
        ((pageHeight - continuationTop - contentBottom) * pixelsPerPoint).toInt().coerceAtLeast(1)
    val rowInk = calculateRowInkPerRow(canvas)
    val searchWindowPx = 48
    val slices = mutableListOf<PdfCanvasSlice>()
    var offsetPx = 0
    var isFirstPage = true
    while (offsetPx < canvas.height) {
        val targetSlicePx = if (isFirstPage) firstPageSlicePxTarget else continuationSlicePxTarget
        val minSliceHeightPx = (targetSlicePx * 0.75).toInt().coerceAtLeast(1)
        val idealEndExclusive = minOf(offsetPx + targetSlicePx, canvas.height)
        val endExclusive =
            if (idealEndExclusive < canvas.height) {
                choosePageSliceEndExclusive(
                    rowInk = rowInk,
                    startInclusive = offsetPx,
                    idealEndExclusive = idealEndExclusive,
                    minSliceHeightPx = minSliceHeightPx,
                    searchWindowPx = searchWindowPx,
                )
            } else {
                canvas.height
            }
        val currentSlicePx = (endExclusive - offsetPx).coerceAtLeast(1)
        slices += PdfCanvasSlice(startPx = offsetPx, heightPx = currentSlicePx, isFirstPage = isFirstPage)
        offsetPx = endExclusive
        isFirstPage = false
    }
    val totalPages = slices.size.coerceAtLeast(1)
    for ((index, slice) in slices.withIndex()) {
        if (index > 0) {
            pdf.addPage()
        }
        if (!slice.isFirstPage) {
            drawContinuationPageHeader(
                pdf = pdf,
                logoUrl = logoUrl,
                documentName = documentName,
                margin = margin,
                pageWidth = pageWidth,
                logoAspectRatio = logoAspectRatio,
            )
        }
        val contentTop =
            if (slice.isFirstPage) {
                margin
            } else {
                continuationTop
            }
        val sliceCanvas = document.createElement("canvas") as HTMLCanvasElement
        sliceCanvas.width = canvas.width
        sliceCanvas.height = slice.heightPx
        val context = sliceCanvas.getContext("2d").unsafeCast<dynamic>()
        context.drawImage(
            canvas,
            0,
            slice.startPx,
            canvas.width,
            slice.heightPx,
            0,
            0,
            canvas.width,
            slice.heightPx,
        )
        val imageData = sliceCanvas.toDataURL("image/png")
        val sliceHeightPt = slice.heightPx.toDouble() / pixelsPerPoint
        pdf.addImage(imageData, "PNG", margin, contentTop, targetWidth, sliceHeightPt, "", "FAST")
        drawPageFooter(
            pdf = pdf,
            exportDateLabel = buildPdfExportDateLabel(exportDateText),
            pageCounterText = buildPdfPageCounterText(pageNumber = index + 1, totalPages = totalPages),
            margin = margin,
            pageHeight = pageHeight,
            pageWidth = pageWidth,
        )
    }
    val targetFile = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
    pdf.save(targetFile)
}

private fun drawContinuationPageHeader(
    pdf: dynamic,
    logoUrl: String,
    documentName: String,
    margin: Double,
    pageWidth: Double,
    logoAspectRatio: Double,
) {
    val logoSize = calculateLogoSizeForHeader(maxHeight = 13.0, aspectRatio = logoAspectRatio)
    val logoHeight = logoSize.height
    val logoWidth = logoSize.width
    val logoY = margin + 1.0
    pdf.addImage(logoUrl, "PNG", margin, logoY, logoWidth, logoHeight, "", "FAST")
    pdf.setTextColor(17, 24, 39)
    pdf.setFont("helvetica", "bold")
    pdf.setFontSize(11)
    val text = buildContinuationPageHeaderText(documentName)
    val textY = margin + 11.0
    pdf.text(text, margin + logoWidth + 8.0, textY)
    pdf.setDrawColor(229, 231, 235)
    pdf.setLineWidth(0.6)
    val lineY = margin + 18.0
    pdf.line(margin, lineY, pageWidth - margin, lineY)
}

internal fun buildContinuationPageHeaderText(documentName: String): String = "Mosa - $documentName"

internal fun buildPdfPageCounterText(
    pageNumber: Int,
    totalPages: Int,
): String = "Seite $pageNumber von $totalPages"

internal fun buildPdfExportDateLabel(exportDateText: String): String = "Export: $exportDateText"

internal fun calculateLogoSizeForHeader(
    maxHeight: Double,
    aspectRatio: Double,
): PdfImageSize {
    val safeAspectRatio = if (aspectRatio > 0.0) aspectRatio else 1.0
    return PdfImageSize(width = maxHeight * safeAspectRatio, height = maxHeight)
}

private fun resolvePdfDocumentName(source: HTMLDivElement): String {
    val headingText = source.querySelector("h1")?.textContent?.trim().orEmpty()
    return headingText.ifBlank { "Dokument" }
}

private fun resolvePdfExportDateText(): String {
    val now = Date()
    val year = now.getUTCFullYear().toString()
    val month = (now.getUTCMonth() + 1).toString().padStart(2, '0')
    val day = now.getUTCDate().toString().padStart(2, '0')
    return "$year-$month-$day"
}

private fun drawPageFooter(
    pdf: dynamic,
    exportDateLabel: String,
    pageCounterText: String,
    margin: Double,
    pageHeight: Double,
    pageWidth: Double,
) {
    val footerBandHeight = 22.0
    pdf.setDrawColor(229, 231, 235)
    pdf.setLineWidth(0.6)
    val lineY = pageHeight - margin - footerBandHeight + 6.0
    pdf.line(margin, lineY, pageWidth - margin, lineY)
    pdf.setTextColor(55, 65, 81)
    pdf.setFont("helvetica", "normal")
    pdf.setFontSize(9)
    val textY = pageHeight - margin - 3.0
    pdf.text(exportDateLabel, margin, textY)
    val counterWidth = (pdf.getTextWidth(pageCounterText) as Number).toDouble()
    val counterX = calculateRightAlignedTextX(pageWidth, margin, counterWidth)
    pdf.text(pageCounterText, counterX, textY)
}

private data class PdfCanvasSlice(
    val startPx: Int,
    val heightPx: Int,
    val isFirstPage: Boolean,
)

internal data class PdfImageSize(
    val width: Double,
    val height: Double,
)

internal fun calculateRightAlignedTextX(
    pageWidth: Double,
    margin: Double,
    textWidth: Double,
): Double = (pageWidth - margin - textWidth).coerceAtLeast(margin)

private fun resolveLogoAspectRatio(exportRoot: HTMLDivElement): Double {
    val logo = exportRoot.querySelector(".legal-pdf-export__logo") as? HTMLImageElement ?: return 1.0
    val naturalWidth = logo.naturalWidth.toDouble()
    val naturalHeight = logo.naturalHeight.toDouble()
    if (naturalWidth <= 0.0 || naturalHeight <= 0.0) return 1.0
    return naturalWidth / naturalHeight
}

internal fun choosePageSliceEndExclusive(
    rowInk: IntArray,
    startInclusive: Int,
    idealEndExclusive: Int,
    minSliceHeightPx: Int,
    searchWindowPx: Int,
): Int {
    if (rowInk.isEmpty()) return 0
    val maxEndExclusive = rowInk.size
    val minEndExclusive = (startInclusive + minSliceHeightPx).coerceAtMost(maxEndExclusive)
    val normalizedIdealEndExclusive = idealEndExclusive.coerceIn(minEndExclusive, maxEndExclusive)
    val searchStart = (normalizedIdealEndExclusive - searchWindowPx).coerceAtLeast(minEndExclusive)
    val searchEnd = (normalizedIdealEndExclusive + searchWindowPx).coerceAtMost(maxEndExclusive)
    var bestEndExclusive = normalizedIdealEndExclusive
    var bestBoundaryInk = Int.MAX_VALUE
    var bestDistance = Int.MAX_VALUE
    for (candidateEndExclusive in searchStart..searchEnd) {
        val candidateBoundaryInk = calculateBoundaryInkScore(rowInk, candidateEndExclusive)
        val candidateDistance = abs(candidateEndExclusive - normalizedIdealEndExclusive)
        val isBetterInk = candidateBoundaryInk < bestBoundaryInk
        val isSameInkAndNearer = candidateBoundaryInk == bestBoundaryInk && candidateDistance < bestDistance
        val isSameInkAndDistanceButLater =
            candidateBoundaryInk == bestBoundaryInk &&
                candidateDistance == bestDistance &&
                candidateEndExclusive > bestEndExclusive
        if (isBetterInk || isSameInkAndNearer || isSameInkAndDistanceButLater) {
            bestEndExclusive = candidateEndExclusive
            bestBoundaryInk = candidateBoundaryInk
            bestDistance = candidateDistance
        }
    }
    return bestEndExclusive.coerceIn(minEndExclusive, maxEndExclusive)
}

private fun calculateBoundaryInkScore(
    rowInk: IntArray,
    endExclusive: Int,
): Int {
    val startRow = (endExclusive - 3).coerceAtLeast(0)
    val endRow = (endExclusive + 2).coerceAtMost(rowInk.lastIndex)
    var sum = 0
    for (rowIndex in startRow..endRow) {
        sum += rowInk[rowIndex]
    }
    return sum
}

private fun calculateRowInkPerRow(canvas: HTMLCanvasElement): IntArray {
    val context =
        canvas.getContext("2d")
            ?.unsafeCast<CanvasRenderingContext2D>()
            ?: return IntArray(canvas.height)
    val imageData = context.getImageData(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
    val pixels = imageData.data.asDynamic()
    val rowInk = IntArray(canvas.height)
    val width = canvas.width
    var pixelOffset = 0
    for (rowIndex in 0 until canvas.height) {
        var totalInk = 0
        for (columnIndex in 0 until width) {
            val red = (pixels[pixelOffset] as Number).toInt()
            val green = (pixels[pixelOffset + 1] as Number).toInt()
            val blue = (pixels[pixelOffset + 2] as Number).toInt()
            val alpha = (pixels[pixelOffset + 3] as Number).toInt()
            if (alpha >= 16) {
                val darkness = 765 - red - green - blue
                if (darkness > 24) {
                    totalInk += darkness
                }
            }
            pixelOffset += 4
        }
        rowInk[rowIndex] = totalInk
    }
    return rowInk
}
