package straightway.mosa.client.legal

import kotlinx.browser.document
import kotlin.js.unsafeCast
import web.dom.Element
import web.html.HTMLDivElement
import web.dom.Element as WebElement

private val comparisonMarkdownRenderer = MarkdownToHtmlRenderer()

internal fun HTMLDivElement.updateLegalRail(
    apply: (List<LegalRailMarker>, LegalViewportIndicator) -> Unit,
) {
    val rawMarkers = collectLegalScrollMarkers()
    val markers = quantizeRailMarkers(rawMarkers, clientHeight)
    val indicator =
        computeLegalViewportIndicator(
            scrollTop = scrollTop,
            scrollHeight = scrollHeight.toDouble(),
            clientHeight = clientHeight.toDouble(),
        )
    apply(markers, indicator)
}

private fun HTMLDivElement.collectLegalScrollMarkers(): List<LegalRailMarker> {
    val totalHeight = scrollHeight.toDouble()
    if (totalHeight <= 0.0) {
        return emptyList()
    }
    val contentRect = getBoundingClientRect()
    val nodes = querySelectorAll(".legal-ct-bg-yellow, .legal-ct-bg-red")
    val markers = mutableListOf<LegalRailMarker>()
    for (index in 0 until nodes.length) {
        val node = nodes.item(index) ?: continue
        val severity =
            when {
                node.className.toString().contains("legal-ct-bg-red") -> LegalMarkerSeverity.RED
                node.className.toString().contains("legal-ct-bg-yellow") -> LegalMarkerSeverity.YELLOW
                else -> null
            } ?: continue
        val nodeTop = node.getBoundingClientRect().top - contentRect.top + scrollTop
        val topPercent = ((nodeTop / totalHeight) * 100.0).coerceIn(0.0, 100.0)
        markers += LegalRailMarker(topPercent = topPercent, severity = severity)
    }
    return markers
}

internal fun HTMLDivElement.refreshComparisonBox(
    selection: LegalComparisonSelection?,
    document: LegalPrivacyPolicyDocument,
    comparisonLabel: String,
    missingComparisonText: String,
    lastAutoScrolledSelectionKey: String,
    updateAutoScrolledSelectionKey: (String) -> Unit,
) {
    removeExistingComparisonBoxes()
    val nextSelection =
        selection ?: run {
            updateAutoScrolledSelectionKey("")
            return
        }
    val uncriticalText =
        document.uncriticalTextByBlockId[nextSelection.blockId]
            ?.ifBlank { missingComparisonText }
            ?: missingComparisonText
    val groupElement = findComparisonGroupElement(nextSelection.groupId) ?: return
    val box = buildComparisonBox(uncriticalText, comparisonLabel)
    mountComparisonBox(groupElement, box)
    val selectionKey = "${nextSelection.groupId}|${nextSelection.blockId}"
    if (lastAutoScrolledSelectionKey != selectionKey) {
        scrollComparisonIntoView(groupElement, box)
        updateAutoScrolledSelectionKey(selectionKey)
    }
}

private fun HTMLDivElement.findComparisonGroupElement(groupId: String): Element? {
    val groups = querySelectorAll("[data-ct-group-id]")
    for (index in 0 until groups.length) {
        val candidate = groups.item(index) ?: continue
        if (candidate.getAttribute("data-ct-group-id") == groupId) {
            return candidate
        }
    }
    return null
}

private fun HTMLDivElement.removeExistingComparisonBoxes() {
    val existing = querySelectorAll(".legal-ct-comparison-box")
    for (index in existing.length - 1 downTo 0) {
        val node = existing.item(index) ?: continue
        node.parentNode?.removeChild(node)
    }
}

private fun buildComparisonBox(
    uncriticalText: String,
    label: String,
): WebElement {
    val box = document.createElement("div")
    box.className = "legal-ct-comparison-box"
    val boxLabel = document.createElement("div")
    boxLabel.className = "legal-ct-comparison-box__label"
    boxLabel.textContent = label
    val boxText = document.createElement("div")
    boxText.className = "legal-ct-comparison-box__text"
    boxText.asDynamic().innerHTML = comparisonMarkdownRenderer.render(uncriticalText)
    box.appendChild(boxLabel)
    box.appendChild(boxText)
    return box.unsafeCast<WebElement>()
}

private fun HTMLDivElement.scrollComparisonIntoView(
    groupElement: Element,
    comparisonBox: WebElement,
) {
    val contentRect = getBoundingClientRect()
    val groupRect = groupElement.getBoundingClientRect()
    val boxRect = comparisonBox.getBoundingClientRect()
    val margin = 12.0
    val top = minOf(groupRect.top, boxRect.top)
    val bottom = maxOf(groupRect.bottom, boxRect.bottom)
    when {
        bottom > contentRect.bottom - margin -> {
            val delta = bottom - contentRect.bottom + margin
            scrollTop += delta
        }
        top < contentRect.top + margin -> {
            val delta = contentRect.top - top + margin
            scrollTop -= delta
        }
    }
}

private fun HTMLDivElement.mountComparisonBox(
    groupElement: Element,
    box: WebElement,
) {
    val nextSibling = groupElement.nextSibling
    if (nextSibling != null) {
        insertBefore(box, nextSibling)
        return
    }
    appendChild(box)
}

internal fun resolveClickableMarker(target: dynamic): Element? {
    var node = target
    while (node != null) {
        val classList = node.classList
        if (classList != null) {
            val isYellow = classList.contains("legal-ct-bg-yellow") as? Boolean ?: false
            val isRed = classList.contains("legal-ct-bg-red") as? Boolean ?: false
            if (isYellow || isRed) {
                return node.unsafeCast<Element>()
            }
        }
        node = node.parentNode
    }
    return null
}

internal fun resolveBlockId(marker: Element): String? {
    val direct = marker.getAttribute("data-ct-block-id")
    if (!direct.isNullOrBlank()) {
        return direct
    }
    val group = marker.closest("[data-ct-block-ids]") ?: return null
    val groupIds = group.getAttribute("data-ct-block-ids") ?: return null
    return groupIds
        .split(' ')
        .map { value -> value.trim() }
        .firstOrNull { value -> value.isNotEmpty() }
}
