package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import straightway.mosa.NanoClock
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.SheddingRequest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestDurationSheddingDeciderTest {
    @Test
    fun `increases shedding when request durations degrade`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 0L,
                        ),
                )

            repeat(5) {
                recordMs(timer, 10)
                assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))
            }

            recordMs(timer, 50, times = 5)

            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/test"))
        }

    @Test
    fun `decreases shedding when request durations recover`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 0L,
                        ),
                )

            repeat(5) {
                recordMs(timer, 10)
                decideFactor(decider, method = "GET", route = "/test")
            }
            recordMs(timer, 50, times = 5)
            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/test"))

            repeat(20) {
                recordMs(timer, 10)
                decideFactor(decider, method = "GET", route = "/test")
            }
            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))
        }

    @Test
    fun `tracks shedding transitions across a duration sequence`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/chat")

            var now = 0L
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { now.also { now += 2_000L } },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 0.5,
                            sheddingDecayAlpha = 0.5,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            data class RequestSurationTestSample(
                val requestDurationMs: Long,
                val expectedSheddingFactorRange: ClosedFloatingPointRange<Double>,
            )

            val samples =
                listOf(
                    RequestSurationTestSample(10L, 0.0..0.01),
                    RequestSurationTestSample(10L, 0.0..0.01),
                    RequestSurationTestSample(10L, 0.0..0.01),
                    RequestSurationTestSample(10L, 0.0..0.01),
                    RequestSurationTestSample(10L, 0.0..0.01),
                    RequestSurationTestSample(50L, 0.45..0.55),
                    RequestSurationTestSample(50L, 0.7..0.8),
                    RequestSurationTestSample(50L, 0.85..0.9),
                    RequestSurationTestSample(10L, 0.9..0.98),
                    RequestSurationTestSample(10L, 0.95..1.0),
                    RequestSurationTestSample(10L, 0.75..0.85),
                    RequestSurationTestSample(10L, 0.5..0.65),
                    RequestSurationTestSample(10L, 0.3..0.45),
                    RequestSurationTestSample(10L, 0.15..0.25),
                    RequestSurationTestSample(10L, 0.05..0.15),
                )

            val actualShedding =
                samples.map { expectation ->
                    recordMs(timer, expectation.requestDurationMs)
                    decideFactor(decider, method = "GET", route = "/chat")
                }

            samples.zip(actualShedding).forEach { (expectation, actualFactor) ->
                assertTrue(actualFactor in expectation.expectedSheddingFactorRange)
            }
        }

    @Test
    fun `recovers from full shedding without new samples`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/sealed")

            var now = 0L
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { now.also { now += 2_000L } },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            repeat(5) {
                recordMs(timer, 10)
                decideFactor(decider, method = "GET", route = "/sealed")
            }

            recordMs(timer, 1_000)
            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/sealed"))

            val recovered = decideFactor(decider, method = "GET", route = "/sealed")
            assertEquals(0.0, recovered)

            val staysOpen = decideFactor(decider, method = "GET", route = "/sealed")
            assertEquals(0.0, staysOpen)
        }

    @Test
    fun `does not shed when no timer exists`() =
        runTest {
            val decider =
                deciderFor(
                    SimpleMeterRegistry(),
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 0,
                            sampleIntervalNs = 0L,
                        ),
                )

            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/missing"))
        }

    @Test
    fun `does not create route state when timer is missing`() =
        runTest {
            val requestDurationForRouteStateFactory = mockk<RequestDurationForRouteState.Factory>(relaxed = true)
            val decider = deciderFor(SimpleMeterRegistry(), requestDurationForRouteStateFactory)

            decideFactor(decider, method = "GET", route = "/missing")

            verify(exactly = 0) { requestDurationForRouteStateFactory.create(any()) }
        }

    @Test
    fun `keeps route state for each seen route`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            timerFor(meterRegistry, method = "GET", route = "/a")
            timerFor(meterRegistry, method = "GET", route = "/b")
            val requestDurationForRouteStateFactory = mockk<RequestDurationForRouteState.Factory>(relaxed = true)

            val decider = deciderFor(meterRegistry, requestDurationForRouteStateFactory)

            decideFactor(decider, method = "GET", route = "/a")
            decideFactor(decider, method = "GET", route = "/b")

            verify(exactly = 2) { requestDurationForRouteStateFactory.create(any()) }
        }

    @Test
    fun `respects sample interval by returning cached result`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val nanoTimes = ArrayDeque(listOf(1_000L, 1_500L, 2_000L))
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { nanoTimes.removeFirst() },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 0,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            recordMs(timer, 10)
            val first = decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            val cached = decideFactor(decider, method = "GET", route = "/test")

            assertEquals(first, cached)

            val next = decideFactor(decider, method = "GET", route = "/test")
            assertEquals(1.0, next)
        }

    @Test
    fun `does not shed when method does not match timer tag`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")
            recordMs(timer, 1_000)

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 0,
                            sampleIntervalNs = 0L,
                        ),
                )

            assertEquals(0.0, decideFactor(decider, method = "POST", route = "/test"))
        }

    @Test
    fun `does not shed when ratio stays within slack`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.5,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 0L,
                        ),
                )

            recordMs(timer, 10)
            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))

            recordMs(timer, 14)
            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))
        }

    @Test
    fun `applies shedding alpha when updating factor`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 0.5,
                            sheddingDecayAlpha = 0.5,
                            sampleIntervalNs = 0L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            assertEquals(0.5, decideFactor(decider, method = "GET", route = "/test"))
        }

    @Test
    fun `decays shedding when no new samples are observed`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val nanoTimes = ArrayDeque(listOf(0L, 1_000L, 2_000L))
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { nanoTimes.removeFirst() },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/test"))

            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))
        }

    @Test
    fun `decays shedding based on elapsed sample intervals without new samples`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val nanoTimes = ArrayDeque(listOf(0L, 2_000L, 4_000L))
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { nanoTimes.removeFirst() },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 0.5,
                            sheddingDecayAlpha = 0.5,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            val peak = decideFactor(decider, method = "GET", route = "/test")
            assertEquals(0.5, peak)

            val decayed = decideFactor(decider, method = "GET", route = "/test")
            assertEquals(0.125, decayed)
        }

    @Test
    fun `decays shedding with dedicated decay alpha`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val nanoTimes = ArrayDeque(listOf(0L, 1_000L, 3_000L))
            val decider =
                deciderFor(
                    meterRegistry,
                    nanoClock = { nanoTimes.removeFirst() },
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 0.5,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/test"))

            val decayed = decideFactor(decider, method = "GET", route = "/test")
            assertEquals(0.25, decayed, 0.0001)
        }

    @Test
    fun `returns a reason when shedding is active`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer = timerFor(meterRegistry, method = "GET", route = "/test")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingConfig(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                            sampleIntervalNs = 0L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            val decision = decider.decideShedding(SheddingRequest("GET", "/test"))

            assertEquals(1.0, decision.sheddingFactor)
            assertTrue(decision.reason.contains("GET /test"))
            assertTrue(decision.reason.contains("ratio="))
        }

    private fun deciderFor(
        meterRegistry: SimpleMeterRegistry,
        nanoClock: NanoClock = NanoClock { 0L },
        config: RequestDurationSheddingConfig,
    ): RequestDurationSheddingDecider =
        deciderFor(
            meterRegistry,
            { timer -> RequestDurationForRouteState(config, nanoClock, timer, RequestDurationSheddingRatioMapper(config)) },
        )

    private fun deciderFor(
        meterRegistry: SimpleMeterRegistry,
        requestDurationForRouteStateFactory: RequestDurationForRouteState.Factory,
    ): RequestDurationSheddingDecider =
        RequestDurationSheddingDecider(
            RequestDurationForRouteStateRegistry(
                meterRegistry = meterRegistry,
                requestDurationForRouteStateFactory = requestDurationForRouteStateFactory,
            ),
        )

    private fun timerFor(
        meterRegistry: SimpleMeterRegistry,
        @Suppress("SameParameterValue") method: String,
        route: String,
    ): Timer =
        Timer
            .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", method)
            .tag("route", route)
            .register(meterRegistry)

    private fun recordMs(
        timer: Timer,
        durationMs: Long,
        times: Int = 1,
    ) {
        repeat(times) { timer.record(durationMs, TimeUnit.MILLISECONDS) }
    }

    private suspend fun decideFactor(
        decider: RequestDurationSheddingDecider,
        method: String,
        route: String,
    ): Double = decider.decideShedding(SheddingRequest(method, route)).sheddingFactor
}
