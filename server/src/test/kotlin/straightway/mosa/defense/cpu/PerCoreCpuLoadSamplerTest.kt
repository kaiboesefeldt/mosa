package straightway.mosa.defense.cpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PerCoreCpuLoadSamplerTest {
    @Test
    fun `divides load average by processor count`() {
        val sampler = PerCoreCpuLoadSampler(ConstCpuLoadSampler(8.0), processorCount = 4)

        assertEquals(2.0, sampler.sample())
    }

    @Test
    fun `delegates operational state to sampler`() {
        val sampler = PerCoreCpuLoadSampler(ConstCpuLoadSampler(1.0, isOperational = false), processorCount = 2)

        assertFalse(sampler.isOperational)
    }

    @Test
    fun `is not operational on non-positive processor count`() {
        val sampler = PerCoreCpuLoadSampler(ConstCpuLoadSampler(1.0), processorCount = 0)

        assertFalse(sampler.isOperational)
    }

    @Test
    fun `defaults processor count to available processors`() {
        val processors = Runtime.getRuntime().availableProcessors()
        val sampler = PerCoreCpuLoadSampler(ConstCpuLoadSampler(2.0 * processors))

        assertEquals(0 < processors, sampler.isOperational)
        if (sampler.isOperational) {
            assertEquals(2.0, sampler.sample())
        }
    }
}
