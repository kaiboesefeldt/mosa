package straightway.mosa.client.legal

import kotlin.test.Test
import kotlin.test.assertEquals

class LegalPrivacyRailModelTest {
    @Test
    fun `merges marker collisions in same rail pixel with red priority`() {
        val markers =
            listOf(
                LegalRailMarker(topPercent = 10.2, severity = LegalMarkerSeverity.YELLOW),
                LegalRailMarker(topPercent = 10.4, severity = LegalMarkerSeverity.RED),
                LegalRailMarker(topPercent = 35.0, severity = LegalMarkerSeverity.YELLOW),
            )

        val quantized = quantizeRailMarkers(markers, railHeightPx = 100)

        assertEquals(
            listOf(
                LegalRailMarker(topPercent = 10.0, severity = LegalMarkerSeverity.RED),
                LegalRailMarker(topPercent = 35.0, severity = LegalMarkerSeverity.YELLOW),
            ),
            quantized,
        )
    }

    @Test
    fun `returns full height viewport indicator when content does not scroll`() {
        val indicator =
            computeLegalViewportIndicator(
                scrollTop = 0.0,
                scrollHeight = 300.0,
                clientHeight = 300.0,
            )

        assertEquals(LegalViewportIndicator(topPercent = 0.0, heightPercent = 100.0), indicator)
    }

    @Test
    fun `computes viewport indicator size and position for scrollable content`() {
        val indicator =
            computeLegalViewportIndicator(
                scrollTop = 150.0,
                scrollHeight = 600.0,
                clientHeight = 300.0,
            )

        assertEquals(LegalViewportIndicator(topPercent = 25.0, heightPercent = 50.0), indicator)
    }
}
