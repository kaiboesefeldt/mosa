package straightway.mosa.defense.cpu

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
                nanoClock = { times.removeFirst() },
                warmUpSamples = 0,
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
                nanoClock = { times.removeFirst() },
                warmUpSamples = 0,
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
                nanoClock = { times.removeFirst() },
                warmUpSamples = 0,
            )

        assertEquals(0.4, sampler.sample())
        assertEquals(0.4, sampler.sample())
    }

    @Test
    fun `is operational when wrapped sampler is operational`() {
        val sampler = SmoothedCpuLoadSampler(ConstCpuLoadSampler(0.2, isOperational = true))

        assertTrue(sampler.isOperational)
        assertFalse(sampler.sample().isNaN())
    }

    @Test
    fun `is not operational when wrapped sampler is not operational`() {
        val sampler = SmoothedCpuLoadSampler(ConstCpuLoadSampler(0.2, isOperational = false))

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `is available when wrapped sampler is available`() {
        val sampler = SmoothedCpuLoadSampler(ConstCpuLoadSampler(0.2, isAvailable = true))

        assertTrue(sampler.isAvailable)
    }

    @Test
    fun `is not available when wrapped sampler is not available`() {
        val sampler = SmoothedCpuLoadSampler(ConstCpuLoadSampler(0.2, isAvailable = false))

        assertFalse(sampler.isAvailable)
    }

    @Test
    fun `uses half life when smoothing time is derived`() {
        val halfLifeNs = 10_000_000_000L
        val times = ArrayDeque(listOf(0L, halfLifeNs))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.0, 1.0)),
                halfLifeNs = halfLifeNs,
                nanoClock = { times.removeFirst() },
                warmUpSamples = 0,
            )

        assertEquals(0.0, sampler.sample())
        assertEquals(0.5, sampler.sample(), absoluteTolerance = 1e-9)
    }

    @Test
    fun `uses warm up samples during construction`() {
        val halfLifeNs = 10_000_000_000L
        val times = ArrayDeque(listOf(0L, halfLifeNs))
        val sampler =
            SmoothedCpuLoadSampler(
                SequenceCpuLoadSampler(listOf(0.0, 1.0)),
                halfLifeNs = halfLifeNs,
                nanoClock = { times.removeFirst() },
                warmUpSamples = 1,
            )

        assertEquals(1, times.size)
        assertEquals(0.5, sampler.sample(), absoluteTolerance = 1e-9)
    }

    @Test
    fun `formats toString with sampler and smoothing time`() {
        val delegate = ConstCpuLoadSampler(0.1, isOperational = true)
        val halfLifeNs = 1_000_000_000L
        val expectedSmoothingTime = SmoothedCpuLoadSampler.smoothingTimeNsForHalfLifeNs(halfLifeNs)
        val sampler =
            SmoothedCpuLoadSampler(
                delegate,
                halfLifeNs = halfLifeNs,
                nanoClock = { 0L },
            )

        assertEquals("SmoothedCpuLoadSampler($delegate, smoothingTimeNs=$expectedSmoothingTime)", sampler.toString())
    }

    private class SequenceCpuLoadSampler(
        private val samples: List<Double>,
        override val isAvailable: Boolean = true,
        override val isOperational: Boolean = true,
    ) : CpuLoadSampler {
        private var index = 0

        override fun toString() = "SequenceCpuLoadSampler($samples, isOperational=$isOperational)"

        override fun sample(): Double = samples.getOrElse(index++) { samples.last() }
    }
}
