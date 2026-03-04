package straightway.mosa.client.legal

import kotlin.math.roundToInt

data class LegalRailMarker(
    val topPercent: Double,
    val severity: LegalMarkerSeverity,
)

enum class LegalMarkerSeverity {
    YELLOW,
    RED,
}

data class LegalViewportIndicator(
    val topPercent: Double,
    val heightPercent: Double,
)

fun quantizeRailMarkers(
    markers: List<LegalRailMarker>,
    railHeightPx: Int,
): List<LegalRailMarker> {
    if (markers.isEmpty() || railHeightPx <= 0) {
        return emptyList()
    }
    val buckets = linkedMapOf<Int, LegalMarkerSeverity>()
    markers.forEach { marker ->
        val bucket = ((marker.topPercent.coerceIn(0.0, 100.0) * railHeightPx) / 100.0).roundToInt()
        val previous = buckets[bucket]
        buckets[bucket] =
            when {
                previous == LegalMarkerSeverity.RED -> LegalMarkerSeverity.RED
                marker.severity == LegalMarkerSeverity.RED -> LegalMarkerSeverity.RED
                else -> LegalMarkerSeverity.YELLOW
            }
    }
    return buckets
        .entries
        .sortedBy { it.key }
        .map { (bucket, severity) ->
            LegalRailMarker(
                topPercent = ((bucket.toDouble() / railHeightPx.toDouble()) * 100.0).coerceIn(0.0, 100.0),
                severity = severity,
            )
        }
}

fun computeLegalViewportIndicator(
    scrollTop: Double,
    scrollHeight: Double,
    clientHeight: Double,
    minHeightPercent: Double = 6.0,
): LegalViewportIndicator {
    if (scrollHeight <= 0.0 || clientHeight <= 0.0 || scrollHeight <= clientHeight) {
        return LegalViewportIndicator(topPercent = 0.0, heightPercent = 100.0)
    }
    val heightPercent = ((clientHeight / scrollHeight) * 100.0).coerceIn(minHeightPercent, 100.0)
    val maxTopPercent = (100.0 - heightPercent).coerceAtLeast(0.0)
    val topPercent =
        ((scrollTop / (scrollHeight - clientHeight)) * maxTopPercent)
            .coerceIn(0.0, maxTopPercent)
    return LegalViewportIndicator(topPercent = topPercent, heightPercent = heightPercent)
}
