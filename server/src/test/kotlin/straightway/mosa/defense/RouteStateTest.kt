package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteStateTest {
    @Test
    fun `returns cached decision when sampling interval not elapsed`() =
        kotlinx.coroutines.test.runTest {
            val cachedDecision =
                SheddingDecider.Result(
                    sheddingFactor = 0.3,
                    reason = "cached",
                )
            val state =
                RouteState(
                    config = RequestDurationSheddingDecider.Config(sampleIntervalNs = 1_000L),
                    nanoClock = { 500L },
                    samplingGate = SamplingGate(lastSampleNs = 0L),
                    timerWindowState = TimerWindowState { error("timer provider should not be called") },
                    latencyStats =
                        LatencyStats(
                            baselineAlpha = 1.0,
                            currentAlpha = 1.0,
                            baselineLearningMaxSheddingFactor = 1.0,
                        ),
                    sheddingState = SheddingState().also { it.cachedDecision = cachedDecision },
                    minBaselineMs = 1.0,
                )

            assertEquals(cachedDecision, state.decide(SheddingRequest("GET", "/test")))
        }
}
