package straightway.mosa.defense.requestduration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestDurationLatencyStatsTest {
    @Test
    fun `returns ratio when baseline is ready`() {
        val stats =
            RequestDurationLatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 1.0,
                minSamplesForBaseline = 5,
                minBaselineMs = 1.0,
            )

        stats.recordSample(TimerSampler.Sample(count = 5, totalTimeMs = 50.0), smoothedSheddingFactor = 0.0)

        assertEquals(1.0, stats.ratioIfReady())
    }

    @Test
    fun `does not update baseline when shedding is high`() {
        val stats =
            RequestDurationLatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 0.05,
                minSamplesForBaseline = 200,
                minBaselineMs = 1.0,
            )

        stats.recordSample(TimerSampler.Sample(count = 1, totalTimeMs = 10.0), smoothedSheddingFactor = 1.0)

        assertTrue(stats.baselineAvgMs.isNaN())
    }

    @Test
    fun `returns null when baseline sample threshold is not met`() {
        val stats =
            RequestDurationLatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 1.0,
                minSamplesForBaseline = 2,
                minBaselineMs = 1.0,
            )

        stats.recordSample(TimerSampler.Sample(count = 1, totalTimeMs = 10.0), smoothedSheddingFactor = 0.0)

        assertNull(stats.ratioIfReady())
    }

    @Test
    fun `uses min baseline when computing ratio`() {
        val stats =
            RequestDurationLatencyStats(
                baselineAlpha = 1.0,
                currentAlpha = 1.0,
                baselineLearningMaxSheddingFactor = 1.0,
                minSamplesForBaseline = 1,
                minBaselineMs = 1.0,
                baselineAvgMs = 0.5,
                currentAvgMs = 1.0,
                observedRequestCount = 1,
            )

        assertEquals(1.0, stats.ratioIfReady())
    }
}
