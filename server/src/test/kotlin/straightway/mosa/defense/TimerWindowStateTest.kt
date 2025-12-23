package straightway.mosa.defense

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimerWindowStateTest {
    @Test
    fun `returns window for new timer samples`() {
        val meterRegistry = SimpleMeterRegistry()
        val timer = timerFor(meterRegistry)
        val state = TimerWindowState { timer }
        val request = SheddingRequest("GET", "/test")

        repeat(2) { timer.record(10, TimeUnit.MILLISECONDS) }

        val window = state.sample(request)

        assertNotNull(window)
        assertEquals(2L, window.count)
        assertEquals(20.0, window.totalTimeMs, 0.001)
    }

    @Test
    fun `returns null when no new samples arrive`() {
        val meterRegistry = SimpleMeterRegistry()
        val timer = timerFor(meterRegistry)
        val state = TimerWindowState { timer }
        val request = SheddingRequest("GET", "/test")

        timer.record(10, TimeUnit.MILLISECONDS)
        state.sample(request)

        assertNull(state.sample(request))
    }

    private fun timerFor(meterRegistry: SimpleMeterRegistry): Timer =
        Timer
            .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", "GET")
            .tag("route", "/test")
            .register(meterRegistry)
}
