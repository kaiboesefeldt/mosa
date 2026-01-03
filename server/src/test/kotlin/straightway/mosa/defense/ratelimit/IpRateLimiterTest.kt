package straightway.mosa.defense.ratelimit

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import straightway.mosa.NanoClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class IpRateLimiterTest {
    @Test
    fun `allows burst within capacity`() =
        runTest {
            val clock = NanoClock { 0L }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 2))

            val first = limiter.check("10.0.0.1")
            val second = limiter.check("10.0.0.1")

            assertTrue(first.allowed)
            assertTrue(second.allowed)
        }

    @Test
    fun `rejects when bucket is empty`() =
        runTest {
            val clock = NanoClock { 0L }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 1))

            assertTrue(limiter.check("10.0.0.1").allowed)
            val decision = limiter.check("10.0.0.1")

            assertFalse(decision.allowed)
            assertEquals(2, decision.retryAfterSeconds)
        }

    @Test
    fun `refills tokens over time`() =
        runTest {
            var nowNanos = 0L
            val clock = NanoClock { nowNanos }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 1))

            assertTrue(limiter.check("10.0.0.1").allowed)
            assertFalse(limiter.check("10.0.0.1").allowed)

            nowNanos += 1.seconds.inWholeNanoseconds

            assertTrue(limiter.check("10.0.0.1").allowed)
        }

    @Test
    fun `does not refill before interval elapses`() =
        runTest {
            var nowNanos = 0L
            val clock = NanoClock { nowNanos }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 1))

            assertTrue(limiter.check("10.0.0.1").allowed)

            nowNanos += 500_000_000L

            assertFalse(limiter.check("10.0.0.1").allowed)
        }

    @Test
    fun `refills multiple tokens after long delay`() =
        runTest {
            var nowNanos = 0L
            val clock = NanoClock { nowNanos }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 5))

            repeat(5) { assertTrue(limiter.check("10.0.0.1").allowed) }
            assertFalse(limiter.check("10.0.0.1").allowed)

            nowNanos += 3.seconds.inWholeNanoseconds

            assertTrue(limiter.check("10.0.0.1").allowed)
            assertTrue(limiter.check("10.0.0.1").allowed)
            assertTrue(limiter.check("10.0.0.1").allowed)
        }

    @Test
    fun `caps tokens to burst after long delay`() =
        runTest {
            var nowNanos = 0L
            val clock = NanoClock { nowNanos }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 2))

            assertTrue(limiter.check("10.0.0.1").allowed)
            assertTrue(limiter.check("10.0.0.1").allowed)
            assertFalse(limiter.check("10.0.0.1").allowed)

            nowNanos += 120.seconds.inWholeNanoseconds

            assertTrue(limiter.check("10.0.0.1").allowed)
            assertTrue(limiter.check("10.0.0.1").allowed)
            assertFalse(limiter.check("10.0.0.1").allowed)
        }

    @Test
    fun `evicts oldest entry when max entries exceeded`() =
        runTest {
            val clock = NanoClock { 0L }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 1, maxEntries = 1))

            assertTrue(limiter.check("10.0.0.1").allowed)
            assertFalse(limiter.check("10.0.0.1").allowed)

            assertTrue(limiter.check("10.0.0.2").allowed)

            assertTrue(limiter.check("10.0.0.1").allowed)
        }

    @Test
    fun `retry-after reflects token interval`() =
        runTest {
            val clock = NanoClock { 0L }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 10, burst = 1))

            assertTrue(limiter.check("10.0.0.1").allowed)
            val decision = limiter.check("10.0.0.1")

            assertFalse(decision.allowed)
            assertEquals(12, decision.retryAfterSeconds)
        }

    @Test
    fun `allows burst across concurrent checks`() =
        runTest {
            val clock = NanoClock { 0L }
            val limiter = IpRateLimiter(clock, IpRateLimitConfig(permitsPerMinute = 60, burst = 2))

            val results =
                listOf(1, 2, 3)
                    .map {
                        async {
                            limiter.check("10.0.0.1").allowed
                        }
                    }.awaitAll()

            assertEquals(2, results.count { it })
        }
}
