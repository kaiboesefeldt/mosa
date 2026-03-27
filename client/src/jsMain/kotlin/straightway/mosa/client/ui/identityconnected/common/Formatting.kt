package straightway.mosa.client.ui.identityconnected.common

import straightway.mosa.client.ui.*

import kotlin.js.Date
import web.dom.Element
import web.html.HTMLDivElement

internal fun isScrolledNearBottom(element: HTMLDivElement): Boolean {
    val thresholdPx = 24
    return element.scrollTop + element.clientHeight >= element.scrollHeight - thresholdPx
}

internal fun closestAttribute(element: Element?, attribute: String): String? {
    var current = element
    while (current != null) {
        val value = current.getAttribute(attribute)
        if (value != null) return value
        current = current.parentElement
    }
    return null
}

internal fun formatTimestamp(epochSeconds: Long): String =
    Date(epochSeconds.toDouble() * 1000).toLocaleString()

internal fun formatSimilarity(similarity: Float): String =
    (similarity.toDouble().asDynamic().toFixed(3) as String)
