package straightway.mosa.defense

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import kotlin.test.assertTrue

class P2QuantileEstimatorTest {
    @Nested
    inner class Validation {
        @ParameterizedTest
        @ValueSource(doubles = [0.0, 1.0, -0.1, 1.1])
        fun `q must be in (0,1)`(q: Double) {
            assertThrows<IllegalArgumentException> { P2QuantileEstimator(name = "p2", quantile = q) }
        }
    }

    @Nested
    inner class EmptyAndWarmupBehavior {
        @Test
        fun `estimate is NaN when no samples`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.95)
            assertEquals(Double.NaN, est.estimate())
            assertEquals(0L, est.samples)
        }

        @ParameterizedTest
        @ValueSource(doubles = [0.1, 0.5, 0.9, 0.95])
        fun `warmup less than 5 samples returns exact quantile of seen samples`(quantile: Double) {
            val est = P2QuantileEstimator(name = "p2", quantile = quantile)

            val samples = doubleArrayOf(10.0, 2.0, 7.0, 5.0)
            val prefix = ArrayList<Double>()

            samples.forEachIndexed { index, sample ->
                prefix.add(sample)
                est.add(sample)

                val expected = exactQuantile(prefix.toDoubleArray(), quantile)
                val actual = est.estimate()

                assertEquals(
                    (index + 1).toLong(),
                    est.samples,
                    "sample counter should count finite samples",
                )
                assertEquals(
                    expected,
                    actual,
                    0.0,
                    "warmup estimate should be exact for q=$quantile at n=${index + 1}",
                )
            }
        }

        @ParameterizedTest
        @ValueSource(doubles = [0.1, 0.5, 0.9, 0.95])
        fun `warmup exactly 5 samples uses always middle sample`(quantile: Double) {
            val est = P2QuantileEstimator(name = "p2", quantile = quantile)
            val samples = doubleArrayOf(9.0, 1.0, 5.5, 3.0, 7.0)
            samples.forEach { est.add(it) }

            val actual = est.estimate()

            assertEquals(5L, est.samples)
            assertEquals(5.5, actual)
        }
    }

    @Nested
    inner class RobustnessToNanAndInfinity {
        @Test
        fun `NaN and infinities are ignored and do not increment sample counter`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.9)

            est.add(Double.NaN)
            est.add(Double.POSITIVE_INFINITY)
            est.add(Double.NEGATIVE_INFINITY)

            assertEquals(0L, est.samples)
            assertEquals(Double.NaN, est.estimate())
        }
    }

    @Nested
    inner class BasicSanityAndStability {
        @Test
        fun `constant stream remains constant`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.95)
            repeat(1_000) { est.add(42.0) }
            assertEquals(1_000L, est.samples)
            assertEquals(42.0, est.estimate(), 0.0)
        }

        @Test
        fun `monotone increasing stream - estimate stays within min-max`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.95)

            var minSeen = Double.POSITIVE_INFINITY
            var maxSeen = Double.NEGATIVE_INFINITY

            (1..10_000).map { it.toDouble() }.forEach {
                est.add(it)

                minSeen = minOf(minSeen, it)
                maxSeen = maxOf(maxSeen, it)

                val e = est.estimate()
                assertTrue(minSeen <= e && e <= maxSeen, "estimate must stay within observed range")
            }
        }

        @Test
        fun `monotone decreasing stream - estimate stays within min-max`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.95)

            var minSeen = Double.POSITIVE_INFINITY
            var maxSeen = Double.NEGATIVE_INFINITY

            (10_000 downTo 1).map { it.toDouble() }.forEach {
                est.add(it)

                minSeen = minOf(minSeen, it)
                maxSeen = maxOf(maxSeen, it)

                val e = est.estimate()
                assertTrue(minSeen <= e && e <= maxSeen, "estimate must stay within observed range")
            }
        }

        @Test
        fun `handles very large finite values`() {
            val est = P2QuantileEstimator(name = "p2", quantile = 0.99)
            val base = 1e300
            repeat(200) { est.add(base + it) }
            val e = est.estimate()
            assertTrue(e.isFinite())
            assertTrue(base <= e)
        }
    }

    @Nested
    inner class AccuracyAndConvergence {
        @Test
        fun `uniform distribution - p50 within tolerance`() {
            val rnd = Random(1234)
            val n = 60_000
            val samples = DoubleArray(n) { rnd.nextDouble() } // Uniform[0,1)

            val q = 0.50
            val est = P2QuantileEstimator(name = "p2", quantile = q)
            samples.forEach { est.add(it) }

            val expected = exactQuantile(samples, q)
            val actual = est.estimate()

            assertClose(actual, expected, absTol = 0.01, msg = "uniform p50")
        }

        @Test
        fun `uniform distribution - p95 within tolerance`() {
            val rnd = Random(5678)
            val n = 60_000
            val samples = DoubleArray(n) { rnd.nextDouble() }

            val q = 0.95
            val est = P2QuantileEstimator(name = "p2", quantile = q)
            samples.forEach { est.add(it) }

            val expected = exactQuantile(samples, q)
            val actual = est.estimate()

            assertClose(actual, expected, absTol = 0.02, msg = "uniform p95")
        }

        @Test
        fun `heavy tail - p95 within relative tolerance`() {
            val rnd = Random(42)
            val n = 100_000
            val samples =
                DoubleArray(n) {
                    val u = rnd.nextDouble()
                    kotlin.math.exp(u * 7.0)
                }

            val q = 0.95
            val est = P2QuantileEstimator(name = "p2", quantile = q)
            samples.forEach { est.add(it) }

            val expected = exactQuantile(samples, q)
            val actual = est.estimate()

            assertClose(actual, expected, absTol = 0.0, relTol = 0.08, msg = "heavy-tail p95")
        }

        @Test
        fun `step distribution - p95 moves into high step`() {
            val n = 80_000
            val samples = DoubleArray(n) { if (it < n * 0.90) 10.0 else 1000.0 } // 90% small, 10% huge

            val q = 0.95
            val est = P2QuantileEstimator(name = "p2", quantile = q)
            samples.forEach { est.add(it) }

            val expected = exactQuantile(samples, q)
            val actual = est.estimate()

            assertEquals(1000.0, expected, 0.0)
            assertTrue(actual > 10.0, "p95 should move into the high step region")
            assertClose(actual, expected, absTol = 150.0, msg = "step p95")
        }
    }

    private fun exactQuantile(
        samples: DoubleArray,
        q: Double,
    ): Double {
        val s = samples.clone()
        s.sort()
        val idx = ((s.size - 1) * q).toInt()
        return s[idx]
    }

    private fun assertClose(
        actual: Double,
        expected: Double,
        absTol: Double,
        relTol: Double = 0.0,
        msg: String = "",
    ) {
        val rel = if (expected == 0.0) absTol else relTol * abs(expected)
        val tol = max(absTol, rel)
        assertTrue(abs(actual - expected) <= tol, "$msg expected=$expected actual=$actual tol=$tol")
    }
}
