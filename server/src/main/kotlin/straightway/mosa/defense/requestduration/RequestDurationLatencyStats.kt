package straightway.mosa.defense.requestduration

import kotlin.math.max

class RequestDurationLatencyStats(
    private val baselineAlpha: Double = 0.02,
    private val currentAlpha: Double = 0.35,
    private val baselineLearningMaxSheddingFactor: Double = 0.05,
    private val minSamplesForBaseline: Long,
    private val minBaselineMs: Double,
    @Volatile var baselineAvgMs: Double = Double.NaN,
    @Volatile var currentAvgMs: Double = Double.NaN,
    @Volatile var observedRequestCount: Long = 0L,
) {
    fun recordSample(
        sample: TimerSampler.Sample,
        smoothedSheddingFactor: Double,
    ) {
        observedRequestCount += sample.count
        currentAvgMs = ewmaMovingAverage(currentAvgMs, sample.averageDurationMs, currentAlpha)
        if (smoothedSheddingFactor <= baselineLearningMaxSheddingFactor) {
            baselineAvgMs = ewmaMovingAverage(baselineAvgMs, sample.averageDurationMs, baselineAlpha)
        }
    }

    fun ratioIfReady(): Double? {
        if (observedRequestCount < minSamplesForBaseline) return null
        if (!baselineAvgMs.isFinite() || !currentAvgMs.isFinite()) return null
        return currentAvgMs / max(baselineAvgMs, minBaselineMs)
    }
}
