package straightway.mosa.defense.requestduration

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import straightway.mosa.NanoClock
import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest

class RequestDurationSamplingGate(
    private val sampleIntervalNs: Long,
    private val sheddingState: RequestDurationSheddingState,
    private val nanoClock: NanoClock,
) {
    @Volatile private var noResamplingBefore: Long = 0L
    private val mutex = Mutex()

    suspend fun computeIfSamplingDue(
        request: SheddingRequest,
        computeDecision: (SheddingRequest, Long) -> SheddingDecider.Result,
    ): SheddingDecider.Result {
        val nowNs = nanoClock.nanoTime()
        if (isWithinSamplingInterval(nowNs)) return sheddingState.cachedDecision

        return mutex.withLock {
            computeDecision(request, nowNs).also {
                sheddingState.cacheDecision(it)
                noResamplingBefore = nowNs + sampleIntervalNs
            }
        }
    }

    private fun isWithinSamplingInterval(nowNs: Long) = nowNs < noResamplingBefore
}
