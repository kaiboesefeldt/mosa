package straightway.mosa.defense

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.max

class RouteState(
    private val config: RequestDurationSheddingDecider.Config,
    private val nanoTime: () -> Long,
    private val samplingGate: SamplingGate,
    private val timerWindowState: TimerWindowState,
    private val latencyStats: LatencyStats,
    private val sheddingState: SheddingState,
    private val minBaselineMs: Double,
) {
    private val mutex = Mutex()

    suspend fun decide(request: SheddingRequest): SheddingDecider.Result =
        nanoTime().let { nowNs ->
            if (isSamplingIntervalNotElapsed(nowNs)) {
                sheddingState.cachedDecision
            } else {
                mutex.withLock {
                    computeDecisionIfSamplingDue(request, nowNs)
                }
            }
        }

    private fun isSamplingIntervalNotElapsed(nowNs: Long): Boolean = samplingGate.isWithinSamplingInterval(nowNs, config.sampleIntervalNs)

    private fun computeDecisionIfSamplingDue(
        request: SheddingRequest,
        nowNs: Long,
    ): SheddingDecider.Result {
        if (isSamplingIntervalNotElapsed(nowNs)) return sheddingState.cachedDecision

        updateDecisionStateFromTimer(request)
        samplingGate.lastSampleNs = nowNs
        sheddingState.cachedDecision = buildResult(request)
        return sheddingState.cachedDecision
    }

    private fun updateDecisionStateFromTimer(request: SheddingRequest) {
        val window = sampleNewTimerWindow(request)
        when {
            window == null -> decaySheddingWithoutNewSamples()
            else -> incorporateTimerWindow(window)
        }
    }

    private fun sampleNewTimerWindow(request: SheddingRequest): TimeWindow? = timerWindowState.sample(request)

    private fun decaySheddingWithoutNewSamples() {
        sheddingState.smoothedSheddingFactor =
            ewmaMovingAverage(sheddingState.smoothedSheddingFactor, 0.0, config.sheddingAlpha)
    }

    private fun incorporateTimerWindow(window: TimeWindow) {
        if (window.count <= 0L || !window.totalTimeMs.isFinite()) return

        val avgMs = averageDurationMs(window)
        latencyStats.recordSamples(window.count, avgMs, sheddingState.smoothedSheddingFactor)
        sheddingState.smoothedSheddingFactor =
            ewmaMovingAverage(sheddingState.smoothedSheddingFactor, computeTargetSheddingFactor(), config.sheddingAlpha)
    }

    private fun averageDurationMs(window: TimeWindow): Double = (window.totalTimeMs / window.count).coerceAtLeast(0.0)

    private fun computeTargetSheddingFactor(): Double {
        val ratio = latencyStats.ratioIfReady(config.minSamplesForBaseline, minBaselineMs) ?: return 0.0
        return mapRatioToTargetSheddingFactor(ratio)
    }

    private fun mapRatioToTargetSheddingFactor(ratio: Double): Double {
        if (!ratio.isFinite()) return 0.0
        val start = 1.0 + config.ratioSlack
        if (ratio <= start) return 0.0
        val span = config.fullSheddingRatio - start
        return ((ratio - start) / span).coerceIn(0.0, 1.0)
    }

    private fun buildResult(request: SheddingRequest): SheddingDecider.Result {
        val factor = sheddingState.smoothedSheddingFactor.coerceIn(0.0, 1.0)
        if (factor <= 0.0) return SheddingDecider.Result.NO_SHEDDING

        val baseline = finiteOrNaN(latencyStats.baselineAvgMs)
        val current = finiteOrNaN(latencyStats.currentAvgMs)
        val ratio = ratioOrNaN(baseline, current)
        val reason = buildReason(request, baseline, current, ratio)

        return SheddingDecider.Result(
            sheddingFactor = factor,
            reason = reason,
        )
    }

    private fun finiteOrNaN(value: Double): Double = value.takeIf { it.isFinite() } ?: Double.NaN

    private fun ratioOrNaN(
        baseline: Double,
        current: Double,
    ): Double =
        if (baseline.isFinite() && current.isFinite()) {
            current / max(baseline, minBaselineMs)
        } else {
            Double.NaN
        }

    private fun buildReason(
        request: SheddingRequest,
        baseline: Double,
        current: Double,
        ratio: Double,
    ): String {
        val avg = formatMs(current)
        val baselineText = formatMs(baseline)
        val ratioText = formatRatio(ratio)

        return "Request duration elevated for ${request.method} ${request.route}: avg=$avg baseline=$baselineText ratio=$ratioText"
    }

    private fun formatMs(v: Double): String =
        if (v.isFinite()) {
            "${"%.2f".format(Locale.US, v)}ms"
        } else {
            "n/a"
        }

    private fun formatRatio(v: Double): String =
        if (v.isFinite()) {
            "%.2f".format(Locale.US, v)
        } else {
            "n/a"
        }
}
