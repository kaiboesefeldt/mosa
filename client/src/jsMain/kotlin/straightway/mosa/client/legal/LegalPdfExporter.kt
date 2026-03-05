package straightway.mosa.client.legal

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlin.js.Promise
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.HTMLCanvasElement

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
    val exportRoot = buildLegalPdfExportRoot(source, logoUrl)
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
        saveCanvasAsPdf(canvas, fileName)
    } finally {
        exportRoot.remove()
    }
}

internal fun buildLegalPdfExportRoot(
    source: HTMLDivElement,
    logoUrl: String,
): HTMLDivElement {
    val root = document.createElement("div") as HTMLDivElement
    root.className = "legal-pdf-export"
    root.style.padding = "8px 10px 2px"
    root.style.color = "#111827"
    root.style.fontFamily = "Segoe UI, Roboto, Helvetica, Arial, sans-serif"

    val title = document.createElement("div") as HTMLDivElement
    title.className = "legal-pdf-export__title"
    title.style.display = "flex"
    title.style.justifyContent = "center"
    title.style.margin = "0 0 18px 0"

    val logo = document.createElement("img") as HTMLImageElement
    logo.className = "legal-pdf-export__logo"
    logo.src = logoUrl
    logo.alt = "Mosa"
    logo.style.width = "140px"
    logo.style.height = "auto"
    logo.style.display = "block"
    title.appendChild(logo)
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
) {
    val pdfConfig: dynamic = js("({})")
    pdfConfig.orientation = "portrait"
    pdfConfig.unit = "pt"
    pdfConfig.format = "a4"
    val pdf = jspdfModule.jsPDF(pdfConfig)
    val pageWidth = pdf.internal.pageSize.getWidth() as Double
    val pageHeight = pdf.internal.pageSize.getHeight() as Double
    val margin = 28.0
    val targetWidth = pageWidth - (margin * 2.0)
    val pixelsPerPoint = canvas.width.toDouble() / targetWidth
    val pageSlicePx = ((pageHeight - (margin * 2.0)) * pixelsPerPoint).toInt().coerceAtLeast(1)
    var offsetPx = 0
    var isFirstPage = true
    while (offsetPx < canvas.height) {
        val currentSlicePx = minOf(pageSlicePx, canvas.height - offsetPx)
        val sliceCanvas = document.createElement("canvas") as HTMLCanvasElement
        sliceCanvas.width = canvas.width
        sliceCanvas.height = currentSlicePx
        val context = sliceCanvas.getContext("2d").unsafeCast<dynamic>()
        context.drawImage(
            canvas,
            0,
            offsetPx,
            canvas.width,
            currentSlicePx,
            0,
            0,
            canvas.width,
            currentSlicePx,
        )
        val imageData = sliceCanvas.toDataURL("image/png")
        val sliceHeightPt = currentSlicePx.toDouble() / pixelsPerPoint
        if (!isFirstPage) {
            pdf.addPage()
        }
        pdf.addImage(imageData, "PNG", margin, margin, targetWidth, sliceHeightPt, "", "FAST")
        offsetPx += currentSlicePx
        isFirstPage = false
    }
    val targetFile = if (fileName.endsWith(".pdf")) fileName else "$fileName.pdf"
    pdf.save(targetFile)
}
