package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FreezeWhenEstimationConvergedTest {
    @Test
    fun `requires positive chunk size`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(1.0))

        assertFailsWith<IllegalArgumentException> {
            FreezeWhenEstimationConverged(raw = raw, chunkSize = 0)
        }
    }

    @Test
    fun `requires positive stability factor`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(1.0))

        assertFailsWith<IllegalArgumentException> {
            FreezeWhenEstimationConverged(raw = raw, stabilityFactor = 0.0)
        }
    }

    @Test
    fun `returns NaN before convergence`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(1.0, 2.0, 3.0))
        val stabilized = FreezeWhenEstimationConverged(raw = raw, chunkSize = 3, stabilityFactor = 0.01)

        stabilized.add(123.0)

        assertEquals(Double.NaN, stabilized.estimate())
    }

    @Test
    fun `freezes when consecutive chunk averages stay within tolerance`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = List(6) { 10.0 })
        val stabilized = FreezeWhenEstimationConverged(raw = raw, chunkSize = 3, stabilityFactor = 0.01, epsilon = 0.0)

        repeat(6) { stabilized.add(123.0) }

        assertEquals(6, raw.addCalls)
        assertEquals(10.0, stabilized.estimate())

        stabilized.add(123.0)

        assertEquals(6, raw.addCalls)
        assertEquals(10.0, stabilized.estimate())
    }

    @Test
    fun `does not freeze when chunk averages deviate beyond tolerance`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(10.0, 10.0, 10.0, 12.0, 12.0, 12.0))
        val stabilized = FreezeWhenEstimationConverged(raw = raw, chunkSize = 3, stabilityFactor = 0.01, epsilon = 0.0)

        repeat(6) { stabilized.add(123.0) }

        assertEquals(6, raw.addCalls)
        assertEquals(Double.NaN, stabilized.estimate())

        stabilized.add(123.0)

        assertEquals(7, raw.addCalls)
    }

    @Test
    fun `uses epsilon as lower tolerance bound`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(0.0, 0.0, 0.0000005, 0.0000005))
        val stabilized = FreezeWhenEstimationConverged(raw = raw, chunkSize = 2, stabilityFactor = 0.01, epsilon = 0.000001)

        repeat(4) { stabilized.add(123.0) }

        assertEquals(4, raw.addCalls)
        assertEquals(0.0000005, stabilized.estimate())

        stabilized.add(123.0)

        assertEquals(4, raw.addCalls)
    }

    @Test
    fun `ignores non finite raw estimates`() {
        val raw = ScriptedEstimator(estimatesAfterAdd = listOf(Double.NaN, 1.0, 1.0, 1.0, 1.0))
        val stabilized = FreezeWhenEstimationConverged(raw = raw, chunkSize = 2, stabilityFactor = 0.01, epsilon = 0.0)

        repeat(4) { stabilized.add(123.0) }

        assertEquals(4, raw.addCalls)

        stabilized.add(123.0)

        assertEquals(5, raw.addCalls)

        stabilized.add(123.0)

        assertEquals(5, raw.addCalls)
        assertEquals(1.0, stabilized.estimate())
    }

    private class ScriptedEstimator(
        override val name: String = "raw",
        private val estimatesAfterAdd: List<Double>,
        initialEstimate: Double = Double.NaN,
    ) : Estimator {
        var addCalls: Int = 0
            private set

        private var currentEstimate: Double = initialEstimate

        override val samples: Long
            get() = addCalls.toLong()

        override fun add(sample: Double) {
            currentEstimate =
                when {
                    addCalls < estimatesAfterAdd.size -> estimatesAfterAdd[addCalls]
                    estimatesAfterAdd.isNotEmpty() -> estimatesAfterAdd.last()
                    else -> currentEstimate
                }
            addCalls += 1
        }

        override fun estimate(): Double = currentEstimate
    }
}
