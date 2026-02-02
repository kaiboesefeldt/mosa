package straightway.mosa.defense.cpu

import com.sun.management.OperatingSystemMXBean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessCpuTimeSamplerTest {
    private val originalOsMxBean =
        java.lang.management.ManagementFactory
            .getOperatingSystemMXBean() as OperatingSystemMXBean

    @Test
    fun `samples cpu load from process cpu time`() {
        var timeNs = 0L
        var cpuTimeNs = 0L
        val sampler =
            ProcessCpuTimeSampler(
                nanoClock = { timeNs },
                minSampleIntervalNs = 1L,
                numberOfProcessors = 1,
                osBean = createFakeOsMxBean { cpuTimeNs },
            )

        assertEquals(Double.NaN, sampler.sample())

        timeNs = 1_000_000_000L
        cpuTimeNs = 1_000_000_000L

        assertEquals(1.0, sampler.sample(), 1e-6)
    }

    @Test
    fun `uses constructor sample as baseline`() {
        var timeNs = 0L
        var cpuTimeNs = 0L
        val sampler =
            ProcessCpuTimeSampler(
                nanoClock = { timeNs },
                minSampleIntervalNs = 1L,
                numberOfProcessors = 1,
                osBean = createFakeOsMxBean { cpuTimeNs },
            )

        timeNs = 1_000L
        cpuTimeNs = 1_000L

        assertEquals(1.0, sampler.sample(), 1e-6)
    }

    @Test
    fun `keeps previous value when interval is too small`() {
        var timeNs = 0L
        var cpuTimeNs = 0L
        val sampler =
            ProcessCpuTimeSampler(
                nanoClock = { timeNs },
                minSampleIntervalNs = 1_000L,
                numberOfProcessors = 1,
                osBean = createFakeOsMxBean { cpuTimeNs },
            )

        assertEquals(Double.NaN, sampler.sample())

        timeNs = 999L
        cpuTimeNs = 999L

        assertEquals(Double.NaN, sampler.sample())

        timeNs = 2_000L
        cpuTimeNs = 2_000L

        assertEquals(1.0, sampler.sample(), 1e-6)
    }

    @Test
    fun `keeps previous value when time or cpu time goes backwards`() {
        var timeNs = 0L
        var cpuTimeNs = 0L
        val sampler =
            ProcessCpuTimeSampler(
                nanoClock = { timeNs },
                minSampleIntervalNs = 1L,
                numberOfProcessors = 1,
                osBean = createFakeOsMxBean { cpuTimeNs },
            )

        assertEquals(Double.NaN, sampler.sample())

        timeNs = 1_000L
        cpuTimeNs = 1_000L

        assertEquals(1.0, sampler.sample(), 1e-6)

        timeNs = 900L
        cpuTimeNs = 1_500L

        assertEquals(1.0, sampler.sample(), 1e-6)

        timeNs = 2_000L
        cpuTimeNs = 1_400L

        assertEquals(0.4, sampler.sample(), 1e-6)

        timeNs = 2_500L
        cpuTimeNs = 1_300L

        assertEquals(0.4, sampler.sample(), 1e-6)

        timeNs = 3_000L
        cpuTimeNs = 1_900L

        assertEquals(0.5, sampler.sample(), 1e-6)
    }

    @Test
    fun `is not operational when process cpu time is negative`() {
        val sampler =
            ProcessCpuTimeSampler(
                osBean = createFakeOsMxBean { -1L },
                numberOfProcessors = 1,
                nanoClock = { 1L },
                minSampleIntervalNs = 1_000L,
            )

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `is operational when process cpu time is non negative`() {
        val sampler =
            ProcessCpuTimeSampler(
                osBean = createFakeOsMxBean { 0L },
                numberOfProcessors = 1,
                nanoClock = { 1L },
                minSampleIntervalNs = 1_000L,
            )

        assertTrue(sampler.isOperational)
    }

    @Test
    fun `requires positive min sample interval`() {
        assertFailsWith<IllegalArgumentException> {
            ProcessCpuTimeSampler(
                osBean = createFakeOsMxBean { 0L },
                numberOfProcessors = 1,
                nanoClock = { 1L },
                minSampleIntervalNs = 0L,
            )
        }
    }

    @Test
    fun `formats toString`() {
        val sampler =
            ProcessCpuTimeSampler(
                osBean = createFakeOsMxBean { 0L },
                numberOfProcessors = 1,
                nanoClock = { 1L },
                minSampleIntervalNs = 1_000L,
            )

        assertEquals("ProcessCpuTimeSampler", sampler.toString())
    }

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    private fun createFakeOsMxBean(processCpuTimeProvider: () -> Long): OperatingSystemMXBean =
        object : OperatingSystemMXBean by originalOsMxBean {
            override fun getProcessCpuTime() = processCpuTimeProvider()
        }
}
