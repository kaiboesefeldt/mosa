package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import straightway.mosa.defense.core.RequestMetricsPlugin
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimerSamplerTest {
    @Test
    fun `returns window for new timer samples`() {
        val meterRegistry = SimpleMeterRegistry()
        val timer = timerFor(meterRegistry)
        val state = TimerSampler(timer)

        repeat(2) { timer.record(10, TimeUnit.MILLISECONDS) }

        val window = state.collectSampleFromTimer()

        assertNotNull(window)
        assertEquals(2L, window.count)
        assertEquals(20.0, window.totalTimeMs, 0.001)
    }

    @Test
    fun `returns null when no new samples arrive`() {
        val meterRegistry = SimpleMeterRegistry()
        val timer = timerFor(meterRegistry)
        val state = TimerSampler(timer)

        timer.record(10, TimeUnit.MILLISECONDS)
        state.collectSampleFromTimer()

        assertNull(state.collectSampleFromTimer())
    }

    @Test
    fun `returns null when timer count moves backwards`() {
        val timer = mockk<Timer>()
        every { timer.count() } returnsMany listOf(5L, 3L)
        every { timer.totalTime(TimeUnit.MILLISECONDS) } returnsMany listOf(50.0, 40.0)
        val state = TimerSampler(timer)

        assertNotNull(state.collectSampleFromTimer())
        assertNull(state.collectSampleFromTimer())
    }

    private fun timerFor(meterRegistry: SimpleMeterRegistry): Timer =
        Timer
            .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", "GET")
            .tag("route", "/test")
            .register(meterRegistry)
}
