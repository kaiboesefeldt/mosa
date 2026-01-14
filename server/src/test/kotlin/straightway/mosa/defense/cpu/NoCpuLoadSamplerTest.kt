package straightway.mosa.defense.cpu

import kotlin.test.Test
import kotlin.test.assertEquals

class NoCpuLoadSamplerTest {
    @Test
    fun `formats toString`() {
        val sampler = NoCpuLoadSampler()

        assertEquals("NoCpuLoadSampler", sampler.toString())
    }
}
