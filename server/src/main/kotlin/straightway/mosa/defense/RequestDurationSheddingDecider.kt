package straightway.mosa.defense

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import straightway.mosa.NanoClock
import java.util.concurrent.ConcurrentHashMap

class RequestDurationSheddingDecider(
    private val meterRegistry: MeterRegistry,
    private val nanoClock: NanoClock,
    val config: Config = Config(),
    private val samplingGateFactory: () -> SamplingGate = { SamplingGate() },
    private val timerWindowStateFactory: ((SheddingRequest) -> Timer?) -> TimerWindowState = { timerProvider ->
        TimerWindowState(timerProvider)
    },
    private val latencyStatsFactory: () -> LatencyStats = {
        LatencyStats(
            baselineAlpha = BASELINE_ALPHA,
            currentAlpha = CURRENT_ALPHA,
            baselineLearningMaxSheddingFactor = BASELINE_LEARNING_MAX_SHEDDING_FACTOR,
        )
    },
    private val sheddingStateFactory: () -> SheddingState = { SheddingState() },
    timerProvider: ((SheddingRequest) -> Timer?)? = null,
) : SheddingDecider {
    data class Config(
        val minSamplesForBaseline: Long = 200,
        val ratioSlack: Double = 0.15,
        val fullSheddingRatio: Double = 2.0,
        val sheddingAlpha: Double = 0.2,
        val sampleIntervalNs: Long = 250_000_000L,
    ) {
        init {
            require(minSamplesForBaseline >= 0) { "minSamplesForBaseline must be non-negative" }
            require(ratioSlack >= 0.0) { "ratioSlack must be non-negative" }
            require(fullSheddingRatio > 1.0 + ratioSlack) { "fullSheddingRatio must be greater than 1.0 + ratioSlack" }
            require(sheddingAlpha > 0.0 && sheddingAlpha <= 1.0) { "sheddingAlpha must be in (0, 1]" }
            require(sampleIntervalNs >= 0L) { "sampleIntervalNs must be non-negative" }
        }
    }

    private val states = ConcurrentHashMap<SheddingRequest, RouteState>()
    private val stateCreationMutex = Mutex()

    private val timerProvider: (SheddingRequest) -> Timer? =
        timerProvider ?: { request -> timerFor(request) }

    override suspend fun decideShedding(request: SheddingRequest): SheddingDecider.Result = getOrCreateRouteState(request).decide(request)

    internal fun stateCount(): Int = states.size

    private suspend fun getOrCreateRouteState(request: SheddingRequest): RouteState =
        states[request] ?: stateCreationMutex.withLock {
            states[request] ?: RouteState(
                config = config,
                nanoClock = nanoClock,
                samplingGate = samplingGateFactory(),
                timerWindowState = timerWindowStateFactory(timerProvider),
                latencyStats = latencyStatsFactory(),
                sheddingState = sheddingStateFactory(),
                minBaselineMs = MIN_BASELINE_MS,
            ).also { state ->
                states[request] = state
            }
        }

    private fun timerFor(request: SheddingRequest): Timer? =
        meterRegistry
            .find(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", request.method)
            .tag("route", request.route)
            .timer()

    companion object {
        private const val MIN_BASELINE_MS: Double = 1.0
        private const val BASELINE_ALPHA: Double = 0.02
        private const val CURRENT_ALPHA: Double = 0.35
        private const val BASELINE_LEARNING_MAX_SHEDDING_FACTOR: Double = 0.05
    }
}
