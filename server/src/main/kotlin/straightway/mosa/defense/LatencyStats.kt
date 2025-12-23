package straightway.mosa.defense

import kotlin.math.max

class LatencyStats(
    private val baselineAlpha: Double,
    private val currentAlpha: Double,
    private val baselineLearningMaxSheddingFactor: Double,
    var baselineAvgMs: Double = Double.NaN,
    var currentAvgMs: Double = Double.NaN,
    var observedRequestCount: Long = 0L,
) {
    fun recordSamples(
        windowCount: Long,
        avgMs: Double,
        smoothedSheddingFactor: Double,
    ) {
        observedRequestCount += windowCount
        currentAvgMs = ewmaMovingAverage(currentAvgMs, avgMs, currentAlpha)
        if (smoothedSheddingFactor <= baselineLearningMaxSheddingFactor) {
            baselineAvgMs = ewmaMovingAverage(baselineAvgMs, avgMs, baselineAlpha)
        }
    }

    fun ratioIfReady(
        minSamplesForBaseline: Long,
        minBaselineMs: Double,
    ): Double? {
        if (observedRequestCount < minSamplesForBaseline) return null
        if (!baselineAvgMs.isFinite() || !currentAvgMs.isFinite()) return null
        return currentAvgMs / max(baselineAvgMs, minBaselineMs)
    }
}
