package straightway.mosa.defense.requestduration

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.util.Locale
import kotlin.math.max

class RequestDurationDecisionEngine(
    private val config: RequestDurationSheddingConfig,
    private val timerSampler: TimerSampler,
    private val requestDurationLatencyStats: RequestDurationLatencyStats,
    private val sheddingState: RequestDurationSheddingState,
    private val ratioMapper: RequestDurationSheddingRatioMapper,
) {
    @Volatile private var lastUpdateNs: Long? = null

    fun updateFromTimer(nowNs: Long) {
        val sample = timerSampler.collectSampleFromTimer()
        if (sample != null) {
            lastUpdateNs = nowNs
            incorporateTimerSample(sample)
            return
        }

        val previousUpdateNs = lastUpdateNs
        lastUpdateNs = nowNs
        if (previousUpdateNs == null) return

        val elapsedNs = nowNs - previousUpdateNs
        val decaySteps = decayStepsForElapsed(elapsedNs)
        if (decaySteps <= 0L) return

        sheddingState.decayWithoutNewSample(decaySteps)
    }

    fun decisionFor(request: SheddingRequest): SheddingDecider.Result {
        val factor = sheddingState.smoothedSheddingFactor.coerceIn(0.0, 1.0)
        if (factor <= 0.0) return SheddingDecider.Result.NO_SHEDDING

        val ratio =
            requestDurationLatencyStats.currentAvgMs / max(requestDurationLatencyStats.baselineAvgMs, config.minBaselineMs)
        val reason = buildSheddingReason(request, ratio)

        return SheddingDecider.Result(
            sheddingFactor = factor,
            reason = reason,
        )
    }

    private fun incorporateTimerSample(sample: TimerSampler.Sample) {
        if (!sample.isValid) return

        requestDurationLatencyStats.recordSample(sample, sheddingState.smoothedSheddingFactor)
        sheddingState.addSample(computeTargetSheddingFactor())
    }

    private fun computeTargetSheddingFactor() =
        requestDurationLatencyStats
            .ratioIfReady()
            ?.let { ratioMapper.targetSheddingFactor(it) }
            ?: 0.0

    private fun buildSheddingReason(
        request: SheddingRequest,
        ratio: Double,
    ): String {
        val avg = requestDurationLatencyStats.currentAvgMs.format("ms")
        val baselineText = requestDurationLatencyStats.baselineAvgMs.format("ms")
        val ratioText = ratio.format()

        return "Request duration elevated for ${request.method} ${request.route}: avg=$avg baseline=$baselineText ratio=$ratioText"
    }

    private fun decayStepsForElapsed(elapsedNs: Long): Long =
        when {
            elapsedNs <= 0L -> 0L
            else -> elapsedNs / config.sampleIntervalNs
        }

    private fun Double.format(unit: String = ""): String =
        if (isFinite()) {
            "${"%.2f".format(Locale.US, this)}$unit"
        } else {
            "n/a"
        }
}
