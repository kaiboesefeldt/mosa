package straightway.mosa.defense.cpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstOperationalCpuLoadSamplerTest {
    @Test
    fun `uses the first operational sampler`() {
        val sampler =
            FirstOperationalCpuLoadSampler(
                ConstCpuLoadSampler(0.1, isOperational = false),
                ConstCpuLoadSampler(0.7, isOperational = true),
                ConstCpuLoadSampler(0.3, isOperational = true),
            )

        assertTrue(sampler.isOperational)
        assertEquals(0.7, sampler.sample())
    }

    @Test
    fun `is not operational no sampler is operational`() {
        val sampler =
            FirstOperationalCpuLoadSampler(
                ConstCpuLoadSampler(0.1, isOperational = false),
                ConstCpuLoadSampler(0.2, isOperational = false),
            )

        assertFalse(sampler.isOperational)
        assertEquals(Double.NaN, sampler.sample())
    }
}
