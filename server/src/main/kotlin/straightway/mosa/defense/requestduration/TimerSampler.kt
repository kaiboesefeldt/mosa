package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class TimerSampler(
    private val timer: Timer,
) {
    private var lastTimerCount: Long = 0L
    private var lastTimerTotalTimeMs: Double = 0.0

    data class Sample(
        val count: Long,
        val totalTimeMs: Double,
    ) {
        val averageDurationMs: Double = (totalTimeMs / count).coerceAtLeast(0.0)
        val isValid: Boolean = 0 < count && totalTimeMs.isFinite()
    }

    fun collectSampleFromTimer(): Sample? {
        val totalCountNow = timer.count()
        val totalTimeNowMs = timer.totalTime(TimeUnit.MILLISECONDS)

        val deltaCount = (totalCountNow - lastTimerCount).coerceAtLeast(0L)
        if (deltaCount == 0L) return null

        val deltaTimeMs = (totalTimeNowMs - lastTimerTotalTimeMs).coerceAtLeast(0.0)

        lastTimerCount = totalCountNow
        lastTimerTotalTimeMs = totalTimeNowMs

        return Sample(deltaCount, deltaTimeMs)
    }
}
