package straightway.mosa.defense

import io.micrometer.core.instrument.Timer
import java.util.concurrent.TimeUnit

class TimerWindowState(
    private val timerProvider: (SheddingRequest) -> Timer?,
) {
    private var timer: Timer? = null
    private var lastTimerCount: Long = 0L
    private var lastTimerTotalTimeMs: Double = 0.0

    fun sample(request: SheddingRequest): TimeWindow? {
        val routeTimer = timer ?: timerProvider(request).also { timer = it }
        return routeTimer?.let { sampleTimeWindowFromTimer(it) }
    }

    private fun sampleTimeWindowFromTimer(timer: Timer): TimeWindow? {
        val totalCountNow = timer.count()
        val totalTimeNowMs = timer.totalTime(TimeUnit.MILLISECONDS)

        val deltaCount = (totalCountNow - lastTimerCount).coerceAtLeast(0L)
        val deltaTimeMs = (totalTimeNowMs - lastTimerTotalTimeMs).coerceAtLeast(0.0)

        lastTimerCount = totalCountNow
        lastTimerTotalTimeMs = totalTimeNowMs

        if (deltaCount == 0L) return null
        return TimeWindow(deltaCount, deltaTimeMs)
    }
}
