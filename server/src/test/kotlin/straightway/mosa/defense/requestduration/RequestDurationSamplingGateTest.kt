package straightway.mosa.defense.requestduration

import kotlinx.coroutines.test.runTest
import straightway.mosa.NanoClock
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDurationSamplingGateTest {
    @Test
    fun `returns cached decision within sampling interval`() =
        runTest {
            val times = ArrayDeque(listOf(0L, 500L, 1_000L))
            val clock = NanoClock { times.removeFirst() }
            val sheddingState =
                RequestDurationSheddingState(
                    RequestDurationSheddingConfig(
                        sheddingSampleAlpha = 1.0,
                        sheddingDecayAlpha = 1.0,
                    ),
                )
            val gate = RequestDurationSamplingGate(sampleIntervalNs = 1_000L, sheddingState = sheddingState, nanoClock = clock)

            var computeCount = 0
            val first =
                gate.computeIfSamplingDue(SheddingRequest("GET", "/test")) { _, _ ->
                    computeCount += 1
                    SheddingDecider.Result(0.3, "first")
                }

            val cached =
                gate.computeIfSamplingDue(SheddingRequest("GET", "/test")) { _, _ ->
                    computeCount += 1
                    SheddingDecider.Result(0.9, "second")
                }

            val next =
                gate.computeIfSamplingDue(SheddingRequest("GET", "/test")) { _, _ ->
                    computeCount += 1
                    SheddingDecider.Result(0.6, "third")
                }

            assertEquals(2, computeCount)
            assertEquals(first, cached)
            assertEquals(0.6, next.sheddingFactor)
        }

    @Test
    fun `returns cached decision when clock does not advance`() =
        runTest {
            val clock = NanoClock { 1_000L }
            val sheddingState =
                RequestDurationSheddingState(
                    RequestDurationSheddingConfig(
                        sheddingSampleAlpha = 1.0,
                        sheddingDecayAlpha = 1.0,
                    ),
                )
            val gate = RequestDurationSamplingGate(sampleIntervalNs = 1_000L, sheddingState = sheddingState, nanoClock = clock)

            var computeCount = 0
            val first =
                gate.computeIfSamplingDue(SheddingRequest("GET", "/test")) { _, _ ->
                    computeCount += 1
                    SheddingDecider.Result(0.2, "first")
                }

            val cached =
                gate.computeIfSamplingDue(SheddingRequest("GET", "/test")) { _, _ ->
                    computeCount += 1
                    SheddingDecider.Result(0.9, "second")
                }

            assertEquals(1, computeCount)
            assertEquals(first, cached)
        }
}
