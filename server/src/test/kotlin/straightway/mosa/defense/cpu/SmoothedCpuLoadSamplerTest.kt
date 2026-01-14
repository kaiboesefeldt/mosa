package straightway.mosa.defense.cpu

import straightway.mosa.NanoClock
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmoothedCpuLoadSamplerTest {
    @Test
    fun `uses the first sample as is`() {
        val times = ArrayDeque(listOf(0L))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.6)),
                halfLifeNs = 1_000_000_000L,
                nanoClock = NanoClock { times.removeFirst() },
            )

        assertEquals(0.6, sampler.sample())
    }

    @Test
    fun `smoothes subsequent samples`() {
        val times = ArrayDeque(listOf(0L, 500_000_000L))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.0, 1.0)),
                halfLifeNs = 1_000_000_000L,
                nanoClock = NanoClock { times.removeFirst() },
            )
        val expected = 1.0 - exp(-0.5 * ln(2.0))

        assertEquals(0.0, sampler.sample())
        assertEquals(expected, sampler.sample(), absoluteTolerance = 1e-9)
    }

    @Test
    fun `keeps previous sample when current sample is not finite`() {
        val times = ArrayDeque(listOf(0L, 1_000_000_000L))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.4, Double.NaN)),
                halfLifeNs = 1_000_000_000L,
                nanoClock = NanoClock { times.removeFirst() },
            )

        assertEquals(0.4, sampler.sample())
        assertEquals(0.4, sampler.sample())
    }

    @Test
    fun `is not operational when wrapped sampler is not operational`() {
        val sampler = SmoothedCpuLoadSampler(ConstCpuLoadSampler(0.2, isOperational = false))

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `uses half life when smoothing time is derived`() {
        val halfLifeNs = 10_000_000_000L
        val times = ArrayDeque(listOf(0L, halfLifeNs))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.0, 1.0)),
                halfLifeNs = halfLifeNs,
                nanoClock = NanoClock { times.removeFirst() },
            )

        assertEquals(0.0, sampler.sample())
        assertEquals(0.5, sampler.sample(), absoluteTolerance = 1e-9)
    }

    private class SequenceCpuLoadSampler(
        private val samples: List<Double>,
        override val isOperational: Boolean = true,
    ) : CpuLoadSampler {
        private var index = 0

        override fun sample(): Double = samples.getOrElse(index++) { samples.last() }
    }
}
