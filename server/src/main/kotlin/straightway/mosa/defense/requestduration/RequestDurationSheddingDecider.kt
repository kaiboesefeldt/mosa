package straightway.mosa.defense.requestduration

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest

class RequestDurationSheddingDecider(
    private val stateRegistry: RequestDurationForRouteStateRegistry,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest): SheddingDecider.Result =
        stateRegistry.stateFor(request)?.decide(request) ?: SheddingDecider.Result.NO_SHEDDING
}
