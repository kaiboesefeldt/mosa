package straightway.mosa.defense

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max

class RequestDurationSheddingDecider(
    private val meterRegistry: MeterRegistry,
    private val nanoTime: () -> Long = System::nanoTime,
    val config: Config = Config(),
) : SheddingDecider {
    data class Config(
        val minSamplesForBaseline: Long = 200,
        val ratioSlack: Double = 0.15,
        val fullSheddingRatio: Double = 2.0,
        val sheddingAlpha: Double = 0.2,
        val sampleIntervalNs: Long = 250_000_000L,
        val maxRoutes: Int = 1_000,
    ) {
        init {
            require(minSamplesForBaseline >= 0) { "minSamplesForBaseline must be non-negative" }
            require(ratioSlack >= 0.0) { "ratioSlack must be non-negative" }
            require(fullSheddingRatio > 1.0 + ratioSlack) { "fullSheddingRatio must be greater than 1.0 + ratioSlack" }
            require(sheddingAlpha > 0.0 && sheddingAlpha <= 1.0) { "sheddingAlpha must be in (0, 1]" }
            require(sampleIntervalNs >= 0L) { "sampleIntervalNs must be non-negative" }
            require(maxRoutes >= 0) { "maxRoutes must be non-negative" }
        }
    }

    private val states = ConcurrentHashMap<SheddingRequest, RouteState>()
    private val stateCreationMutex = Mutex()
    private val stateOrder = ArrayDeque<SheddingRequest>()

    private data class TimeWindow(
        val count: Long,
        val totalTimeMs: Double,
    )

    override suspend fun decideShedding(request: SheddingRequest): SheddingDecider.Result =
        when {
            config.maxRoutes == 0 -> SheddingDecider.Result.NO_SHEDDING
            else -> getOrCreateRouteState(request).decide(request, nanoTime())
        }

    internal fun stateCount(): Int = states.size

    private suspend fun getOrCreateRouteState(request: SheddingRequest): RouteState =
        states[request] ?: stateCreationMutex.withLock {
            states[request] ?: RouteState()
                .also { state ->
                    states[request] = state
                    stateOrder.addLast(request)
                    evictIfNeeded()
                }
        }

    private fun evictIfNeeded() {
        while (stateOrder.size > config.maxRoutes) {
            val evictedRequest = stateOrder.removeFirst()
            states.remove(evictedRequest)
        }
    }

    private fun timerFor(request: SheddingRequest): Timer? =
        meterRegistry
            .find(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", request.method)
            .tag("route", request.route)
            .timer()

    private class SamplingGate(
        @Volatile
        var lastSampleNs: Long = 0L,
    ) {
        fun isWithinSamplingInterval(
            nowNs: Long,
            sampleIntervalNs: Long,
        ): Boolean = nowNs - lastSampleNs < sampleIntervalNs
    }

    private inner class RouteState {
        private val mutex = Mutex()

        private var timer: Timer? = null

        private val samplingGate = SamplingGate()
        private var lastTimerCount: Long = 0L
        private var lastTimerTotalTimeMs: Double = 0.0

        private var baselineAvgMs: Double = Double.NaN
        private var currentAvgMs: Double = Double.NaN
        private var observedRequestCount: Long = 0L

        private var smoothedSheddingFactor: Double = 0.0

        @Volatile
        private var cachedDecision: SheddingDecider.Result = SheddingDecider.Result.NO_SHEDDING

        suspend fun decide(
            request: SheddingRequest,
            nowNs: Long,
        ): SheddingDecider.Result =
            if (isSamplingIntervalNotElapsed(nowNs)) {
                cachedDecision
            } else {
                mutex.withLock {
                    computeDecisionIfSamplingDue(request, nowNs)
                }
            }

        private fun isSamplingIntervalNotElapsed(nowNs: Long): Boolean =
            samplingGate.isWithinSamplingInterval(nowNs, config.sampleIntervalNs)

        private fun computeDecisionIfSamplingDue(
            request: SheddingRequest,
            nowNs: Long,
        ): SheddingDecider.Result {
            if (isSamplingIntervalNotElapsed(nowNs)) return cachedDecision

            updateDecisionStateFromTimer(request)
            samplingGate.lastSampleNs = nowNs
            cachedDecision = buildResult(request)
            return cachedDecision
        }

        private fun updateDecisionStateFromTimer(request: SheddingRequest) {
            val window = sampleNewTimerWindow(request)
            when {
                window == null -> decaySheddingWithoutNewSamples()
                else -> incorporateTimerWindow(window)
            }
        }

        private fun sampleNewTimerWindow(request: SheddingRequest): TimeWindow? {
            val routeTimer = timer ?: timerFor(request).also { timer = it }
            return routeTimer?.let { sampleTimeWindowFromTimer(it) }
        }

        private fun sampleTimeWindowFromTimer(timer: Timer): TimeWindow? {
            val totalCountNow = timer.count()
            val totalTimeNowMs = timer.totalTime(TimeUnit.MILLISECONDS)

            val deltaCount = (totalCountNow - lastTimerCount).coerceAtLeast(0L)
            val deltaTimeMs = (totalTimeNowMs - lastTimerTotalTimeMs).coerceAtLeast(0.0)

            lastTimerCount = totalCountNow
            lastTimerTotalTimeMs = totalTimeNowMs

            if (deltaCount == 0L) return null
            return TimeWindow(deltaCount, deltaTimeMs)
        }

        private fun decaySheddingWithoutNewSamples() {
            smoothedSheddingFactor = ewma(smoothedSheddingFactor, 0.0, config.sheddingAlpha)
        }

        private fun incorporateTimerWindow(window: TimeWindow) {
            if (window.count <= 0L || !window.totalTimeMs.isFinite()) return

            val avgMs = averageDurationMs(window)
            observedRequestCount += window.count
            updateLatencyEstimates(avgMs)
            smoothedSheddingFactor = ewma(smoothedSheddingFactor, computeTargetSheddingFactor(), config.sheddingAlpha)
        }

        private fun averageDurationMs(window: TimeWindow): Double = (window.totalTimeMs / window.count).coerceAtLeast(0.0)

        private fun updateLatencyEstimates(avgMs: Double) {
            currentAvgMs = ewma(currentAvgMs, avgMs, CURRENT_ALPHA)
            if (baselineLearningAllowed()) {
                baselineAvgMs = ewma(baselineAvgMs, avgMs, BASELINE_ALPHA)
            }
        }

        private fun baselineLearningAllowed(): Boolean = smoothedSheddingFactor <= BASELINE_LEARNING_MAX_SHEDDING_FACTOR

        private fun computeTargetSheddingFactor(): Double {
            if (observedRequestCount < config.minSamplesForBaseline) return 0.0
            if (!baselineAvgMs.isFinite() || !currentAvgMs.isFinite()) return 0.0

            val ratio = currentAvgMs / max(baselineAvgMs, MIN_BASELINE_MS)
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
            val factor = smoothedSheddingFactor.coerceIn(0.0, 1.0)
            if (factor <= 0.0) return SheddingDecider.Result.NO_SHEDDING

            val baseline = finiteOrNaN(baselineAvgMs)
            val current = finiteOrNaN(currentAvgMs)
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
                current / max(baseline, MIN_BASELINE_MS)
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
    }

    private fun ewma(
        previous: Double,
        sample: Double,
        alpha: Double,
    ): Double =
        when {
            !sample.isFinite() -> previous
            !previous.isFinite() -> sample
            else -> previous + alpha * (sample - previous)
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

    companion object {
        private const val MIN_BASELINE_MS: Double = 1.0
        private const val BASELINE_ALPHA: Double = 0.02
        private const val CURRENT_ALPHA: Double = 0.35
        private const val BASELINE_LEARNING_MAX_SHEDDING_FACTOR: Double = 0.05
    }
}
