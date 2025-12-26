package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDurationForRouteStateTest {
    @Test
    fun `returns cached decision when sampling interval not elapsed`() =
        kotlinx.coroutines.test.runTest {
            var nanoClock = 500L
            val timer =
                mockk<Timer> {
                    every { count() } returns 1L
                    every { totalTime(TimeUnit.MILLISECONDS) } returns 1.0
                }
            val config = RequestDurationSheddingConfig(sampleIntervalNs = 1_000L, minBaselineMs = 1.0)
            val state =
                RequestDurationForRouteState(
                    config = config,
                    nanoClock = { nanoClock },
                    timer = timer,
                    ratioMapper = RequestDurationSheddingRatioMapper(config),
                    sheddingState =
                        RequestDurationSheddingState(
                            RequestDurationSheddingConfig(
                                sheddingSampleAlpha = 0.2,
                                sheddingDecayAlpha = 0.2,
                            ),
                        ),
                )

            val result1 = state.decide(SheddingRequest("GET", "/test"))
            nanoClock = 1499L
            val result2 = state.decide(SheddingRequest("GET", "/test"))

            verify(exactly = 1) { timer.totalTime(TimeUnit.MILLISECONDS) }
            assertEquals(SheddingDecider.Result.NO_SHEDDING, result1)
            assertEquals(SheddingDecider.Result.NO_SHEDDING, result2)
        }

    @Test
    fun `resamples when sampling interval elapsed`() =
        kotlinx.coroutines.test.runTest {
            var nanoClock = 500L
            val timer =
                mockk<Timer> {
                    every { count() } returns 1L
                    every { totalTime(TimeUnit.MILLISECONDS) } returns 1.0
                }
            val config = RequestDurationSheddingConfig(sampleIntervalNs = 1_000L, minBaselineMs = 1.0)
            val state =
                RequestDurationForRouteState(
                    config = config,
                    nanoClock = { nanoClock },
                    timer = timer,
                    ratioMapper = RequestDurationSheddingRatioMapper(config),
                    sheddingState =
                        RequestDurationSheddingState(
                            RequestDurationSheddingConfig(
                                sheddingSampleAlpha = 0.2,
                                sheddingDecayAlpha = 0.2,
                            ),
                        ),
                )

            val result1 = state.decide(SheddingRequest("GET", "/test"))
            nanoClock = 1500L
            val result2 = state.decide(SheddingRequest("GET", "/test"))

            verify(exactly = 2) { timer.totalTime(TimeUnit.MILLISECONDS) }
            assertEquals(SheddingDecider.Result.NO_SHEDDING, result1)
            assertEquals(SheddingDecider.Result.NO_SHEDDING, result2)
        }

    @Test
    fun `recomputes decision when sampling interval elapsed`() =
        kotlinx.coroutines.test.runTest {
            val cachedDecision =
                SheddingDecider.Result(
                    sheddingFactor = 0.7,
                    reason = "cached",
                )
            val registry = SimpleMeterRegistry()
            val timer = Timer.builder("request.timer").register(registry)
            timer.record(10, TimeUnit.MILLISECONDS)
            val config =
                RequestDurationSheddingConfig(
                    minBaselineMs = 1.0,
                    minSamplesForBaseline = 1,
                    sampleIntervalNs = 1L,
                )
            val state =
                RequestDurationForRouteState(
                    config = config,
                    nanoClock = { 1_000L },
                    timer = timer,
                    ratioMapper = RequestDurationSheddingRatioMapper(config),
                    sheddingState =
                        RequestDurationSheddingState(
                            RequestDurationSheddingConfig(
                                sheddingSampleAlpha = 0.2,
                                sheddingDecayAlpha = 0.2,
                            ),
                        ).also { it.cacheDecision(cachedDecision) },
                )

            assertEquals(SheddingDecider.Result.NO_SHEDDING, state.decide(SheddingRequest("GET", "/test")))
        }
}
