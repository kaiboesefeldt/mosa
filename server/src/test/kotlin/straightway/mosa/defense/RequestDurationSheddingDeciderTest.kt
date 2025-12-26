package straightway.mosa.defense

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.test.runTest
import straightway.mosa.NanoClock
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 5,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
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
    fun `does not shed when no timer exists`() =
        runTest {
            val decider =
                deciderFor(
                    SimpleMeterRegistry(),
                    config =
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 0,
                            sampleIntervalNs = 0L,
                        ),
                )

            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/missing"))
        }

    @Test
    fun `keeps route state for each seen route`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            timerFor(meterRegistry, method = "GET", route = "/a")
            timerFor(meterRegistry, method = "GET", route = "/b")

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingDecider.Config(
                            sampleIntervalNs = 0L,
                        ),
                )

            decideFactor(decider, method = "GET", route = "/a")
            decideFactor(decider, method = "GET", route = "/b")

            assertEquals(2, decider.stateCount())
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 0,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
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
                        RequestDurationSheddingDecider.Config(
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.5,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 0.5,
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

            val decider =
                deciderFor(
                    meterRegistry,
                    config =
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
                            sampleIntervalNs = 0L,
                        ),
                )

            recordMs(timer, 10)
            decideFactor(decider, method = "GET", route = "/test")

            recordMs(timer, 1_000)
            assertEquals(1.0, decideFactor(decider, method = "GET", route = "/test"))

            assertEquals(0.0, decideFactor(decider, method = "GET", route = "/test"))
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
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 1,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
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
        config: RequestDurationSheddingDecider.Config,
    ): RequestDurationSheddingDecider =
        RequestDurationSheddingDecider(
            meterRegistry = meterRegistry,
            nanoClock = nanoClock,
            config = config,
            samplingGateFactory = { SamplingGate() },
            timerWindowStateFactory = { timerProvider -> TimerWindowState(timerProvider) },
            latencyStatsFactory = {
                LatencyStats(
                    baselineAlpha = 0.02,
                    currentAlpha = 0.35,
                    baselineLearningMaxSheddingFactor = 0.05,
                )
            },
            sheddingStateFactory = { SheddingState() },
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
