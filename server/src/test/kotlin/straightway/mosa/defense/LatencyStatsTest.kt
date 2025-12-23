package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LatencyStatsTest {
    @Test
    fun `returns ratio when baseline is ready`() {
        val stats =
            LatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 1.0,
            )

        stats.recordSamples(windowCount = 5, avgMs = 10.0, smoothedSheddingFactor = 0.0)

        assertEquals(1.0, stats.ratioIfReady(minSamplesForBaseline = 5, minBaselineMs = 1.0))
    }

    @Test
    fun `does not update baseline when shedding is high`() {
        val stats =
            LatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 0.05,
            )

        stats.recordSamples(windowCount = 1, avgMs = 10.0, smoothedSheddingFactor = 1.0)

        assertTrue(stats.baselineAvgMs.isNaN())
    }
}
