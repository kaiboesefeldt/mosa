package straightway.mosa.defense

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.test.runTest
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDurationSheddingDeciderTest {
    @Test
    fun `increases shedding when request durations degrade`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer =
                Timer
                    .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
                    .tag("method", "GET")
                    .tag("route", "/test")
                    .register(meterRegistry)

            val decider =
                RequestDurationSheddingDecider(
                    meterRegistry = meterRegistry,
                    nanoTime = { 0L },
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
                timer.record(10, TimeUnit.MILLISECONDS)
                assertEquals(0.0, decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor)
            }

            repeat(5) {
                timer.record(50, TimeUnit.MILLISECONDS)
            }

            assertEquals(1.0, decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor)
        }

    @Test
    fun `decreases shedding when request durations recover`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer =
                Timer
                    .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
                    .tag("method", "GET")
                    .tag("route", "/test")
                    .register(meterRegistry)

            val decider =
                RequestDurationSheddingDecider(
                    meterRegistry = meterRegistry,
                    nanoTime = { 0L },
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
                timer.record(10, TimeUnit.MILLISECONDS)
                decider.decideShedding(SheddingRequest("GET", "/test"))
            }
            repeat(5) {
                timer.record(50, TimeUnit.MILLISECONDS)
            }
            assertEquals(1.0, decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor)

            repeat(20) {
                timer.record(10, TimeUnit.MILLISECONDS)
                decider.decideShedding(SheddingRequest("GET", "/test"))
            }
            assertEquals(0.0, decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor)
        }

    @Test
    fun `does not shed when no timer exists`() =
        runTest {
            val decider =
                RequestDurationSheddingDecider(
                    meterRegistry = SimpleMeterRegistry(),
                    nanoTime = { 0L },
                    config =
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 0,
                            sampleIntervalNs = 0L,
                        ),
                )

            assertEquals(0.0, decider.decideShedding(SheddingRequest("GET", "/missing")).sheddingFactor)
        }

    @Test
    fun `keeps state map bounded`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            Timer
                .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
                .tag("method", "GET")
                .tag("route", "/a")
                .register(meterRegistry)
            Timer
                .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
                .tag("method", "GET")
                .tag("route", "/b")
                .register(meterRegistry)

            val decider =
                RequestDurationSheddingDecider(
                    meterRegistry = meterRegistry,
                    nanoTime = { 0L },
                    config =
                        RequestDurationSheddingDecider.Config(
                            maxRoutes = 1,
                            sampleIntervalNs = 0L,
                        ),
                )

            decider.decideShedding(SheddingRequest("GET", "/a"))
            decider.decideShedding(SheddingRequest("GET", "/b"))

            assertEquals(1, decider.stateCount())
        }

    @Test
    fun `respects sample interval by returning cached result`() =
        runTest {
            val meterRegistry = SimpleMeterRegistry()
            val timer =
                Timer
                    .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
                    .tag("method", "GET")
                    .tag("route", "/test")
                    .register(meterRegistry)

            val nanoTimes = ArrayDeque(listOf(1_000L, 1_500L, 2_000L))
            val decider =
                RequestDurationSheddingDecider(
                    meterRegistry = meterRegistry,
                    nanoTime = { nanoTimes.removeFirst() },
                    config =
                        RequestDurationSheddingDecider.Config(
                            minSamplesForBaseline = 0,
                            ratioSlack = 0.1,
                            fullSheddingRatio = 2.0,
                            sheddingAlpha = 1.0,
                            sampleIntervalNs = 1_000L,
                        ),
                )

            timer.record(10, TimeUnit.MILLISECONDS)
            val first = decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor

            timer.record(1_000, TimeUnit.MILLISECONDS)
            val cached = decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor

            assertEquals(first, cached)

            val next = decider.decideShedding(SheddingRequest("GET", "/test")).sheddingFactor
            assertEquals(1.0, next)
        }
}
