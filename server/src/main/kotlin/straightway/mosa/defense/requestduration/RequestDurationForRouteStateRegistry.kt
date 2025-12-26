package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.SheddingRequest
import java.util.concurrent.ConcurrentHashMap

class RequestDurationForRouteStateRegistry(
    private val meterRegistry: MeterRegistry,
    private val requestDurationForRouteStateFactory: RequestDurationForRouteState.Factory,
) {
    private val states = ConcurrentHashMap<SheddingRequest, RequestDurationForRouteState>()

    fun stateFor(request: SheddingRequest): RequestDurationForRouteState? {
        val timer = timerFor(request) ?: return null
        return states.computeIfAbsent(request) { requestDurationForRouteStateFactory.create(timer) }
    }

    private fun timerFor(request: SheddingRequest): Timer? =
        meterRegistry
            .find(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", request.method)
            .tag("route", request.route)
            .timer()
}
