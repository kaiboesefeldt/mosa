package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestDurationDecisionEngineTest {
    @Test
    fun `returns no shedding when factor is zero`() {
        val config = RequestDurationSheddingConfig(ratioSlack = 0.15, fullSheddingRatio = 2.0)
        val engine =
            RequestDurationDecisionEngine(
                config = config,
                timerSampler = TimerSampler(timerFor(SimpleMeterRegistry())),
                requestDurationLatencyStats = RequestDurationLatencyStats(minSamplesForBaseline = 0, minBaselineMs = 1.0),
                sheddingState =
                    RequestDurationSheddingState(
                        RequestDurationSheddingConfig(
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                        ),
                    ),
                ratioMapper = RequestDurationSheddingRatioMapper(config),
            )

        val decision = engine.decisionFor(SheddingRequest("GET", "/test"))

        assertEquals(SheddingDecider.Result.NO_SHEDDING, decision)
    }

    @Test
    fun `includes request details and ratio in reason when shedding is active`() {
        val config = RequestDurationSheddingConfig(ratioSlack = 0.15, fullSheddingRatio = 2.0)
        val requestDurationLatencyStats =
            RequestDurationLatencyStats(
                minSamplesForBaseline = 0,
                minBaselineMs = 1.0,
                baselineAvgMs = 10.0,
                currentAvgMs = 30.0,
            )
        val engine =
            RequestDurationDecisionEngine(
                config = config,
                timerSampler = TimerSampler(timerFor(SimpleMeterRegistry())),
                requestDurationLatencyStats = requestDurationLatencyStats,
                sheddingState =
                    RequestDurationSheddingState(
                        RequestDurationSheddingConfig(
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                        ),
                        _smoothedSheddingFactor = 0.5,
                    ),
                ratioMapper = RequestDurationSheddingRatioMapper(config),
            )

        val decision = engine.decisionFor(SheddingRequest("POST", "/topic"))

        assertEquals(0.5, decision.sheddingFactor)
        assertTrue(decision.reason.contains("POST /topic"))
        assertTrue(decision.reason.contains("ratio="))
    }

    @Test
    fun `uses n slash a when ratio is not finite`() {
        val config = RequestDurationSheddingConfig(ratioSlack = 0.15, fullSheddingRatio = 2.0)
        val requestDurationLatencyStats =
            RequestDurationLatencyStats(
                minSamplesForBaseline = 0,
                minBaselineMs = 1.0,
                baselineAvgMs = Double.NaN,
                currentAvgMs = 10.0,
            )
        val engine =
            RequestDurationDecisionEngine(
                config = config,
                timerSampler = TimerSampler(timerFor(SimpleMeterRegistry())),
                requestDurationLatencyStats = requestDurationLatencyStats,
                sheddingState =
                    RequestDurationSheddingState(
                        RequestDurationSheddingConfig(
                            sheddingSampleAlpha = 1.0,
                            sheddingDecayAlpha = 1.0,
                        ),
                        _smoothedSheddingFactor = 0.4,
                    ),
                ratioMapper = RequestDurationSheddingRatioMapper(config),
            )

        val decision = engine.decisionFor(SheddingRequest("GET", "/edge"))

        assertEquals(0.4, decision.sheddingFactor)
        assertTrue(decision.reason.contains("ratio=n/a"))
    }

    private fun timerFor(meterRegistry: SimpleMeterRegistry): Timer =
        Timer
            .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", "GET")
            .tag("route", "/test")
            .register(meterRegistry)
}
