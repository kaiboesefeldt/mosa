package straightway.mosa.defense.cpu

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultCpuLoadSamplerTest {
    @Test
    fun `uses oshi sampler when operational`() {
        val sampler =
            defaultCpuLoadSampler(
                oshiSampler = ConstCpuLoadSampler(0.9, isOperational = true),
                processSampler = ConstCpuLoadSampler(0.4, isOperational = true),
                systemLoadAverageSampler = ConstCpuLoadSampler(8.0, isOperational = true),
                processorCount = 4,
            )

        assertEquals(0.9, sampler.sample())
    }

    @Test
    fun `uses process sampler when oshi sampler is not operational`() {
        val sampler =
            defaultCpuLoadSampler(
                oshiSampler = ConstCpuLoadSampler(0.9, isOperational = false),
                processSampler = ConstCpuLoadSampler(0.4, isOperational = true),
                systemLoadAverageSampler = ConstCpuLoadSampler(8.0, isOperational = true),
                processorCount = 4,
            )

        assertEquals(0.4, sampler.sample())
    }

    @Test
    fun `uses per core load average when oshi and process samplers are not operational`() {
        val sampler =
            defaultCpuLoadSampler(
                oshiSampler = ConstCpuLoadSampler(0.9, isOperational = false),
                processSampler = ConstCpuLoadSampler(0.4, isOperational = false),
                systemLoadAverageSampler = ConstCpuLoadSampler(8.0, isOperational = true),
                processorCount = 4,
            )

        assertEquals(2.0, sampler.sample())
    }
}
