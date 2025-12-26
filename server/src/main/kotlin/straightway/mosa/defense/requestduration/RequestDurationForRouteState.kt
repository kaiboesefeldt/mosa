package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import straightway.mosa.NanoClock
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest

class RequestDurationForRouteState(
    private val config: RequestDurationSheddingConfig,
    nanoClock: NanoClock,
    timer: Timer,
    ratioMapper: RequestDurationSheddingRatioMapper,
    private val sheddingState: RequestDurationSheddingState = RequestDurationSheddingState(config),
) {
    fun interface Factory {
        fun create(timer: Timer): RequestDurationForRouteState
    }

    private val requestDurationLatencyStats =
        RequestDurationLatencyStats(
            minSamplesForBaseline = config.minSamplesForBaseline,
            minBaselineMs = config.minBaselineMs,
        )
    private val timerSampler = TimerSampler(timer)
    private val decisionEngine =
        RequestDurationDecisionEngine(
            config = config,
            timerSampler = timerSampler,
            requestDurationLatencyStats = requestDurationLatencyStats,
            sheddingState = sheddingState,
            ratioMapper = ratioMapper,
        )
    private val requestDurationSamplingGate = RequestDurationSamplingGate(config.sampleIntervalNs, sheddingState, nanoClock)

    suspend fun decide(request: SheddingRequest): SheddingDecider.Result =
        requestDurationSamplingGate.computeIfSamplingDue(request) { currentRequest, nowNs ->
            decisionEngine.updateFromTimer(nowNs)
            decisionEngine.decisionFor(currentRequest)
        }
}
