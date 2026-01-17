package straightway.mosa.defense.cpu

import straightway.mosa.NanoClock
import straightway.mosa.defense.requestduration.ewmaMovingAverage
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

class SmoothedCpuLoadSampler(
    private val sampler: CpuLoadSampler,
    halfLifeNs: Long = 10000_000_000L,
    private val nanoClock: NanoClock = NanoClock(System::nanoTime),
    warmUpSamples: Int = 10,
) : CpuLoadSampler {
    private val state = AtomicReference(State(Double.NaN, Long.MIN_VALUE))
    private val smoothingTimeNs = smoothingTimeNsForHalfLifeNs(halfLifeNs)

    init {
        require(0 < halfLifeNs) { "halfLifeNs must be positive" }
        repeat(warmUpSamples) { sample() }
    }

    override val isAvailable: Boolean get() = sampler.isAvailable
    override val isOperational: Boolean get() = sampler.isOperational

    override fun toString() = "SmoothedCpuLoadSampler($sampler, smoothingTimeNs=$smoothingTimeNs)"

    override fun sample(): Double {
        if (!sampler.isOperational) return Double.NaN
        val nowNs = nanoClock.nanoTime()
        val current = sampler.sample()
        return state
            .updateAndGet { previousState ->
                if (!current.isFinite()) {
                    previousState
                } else {
                    val alpha = alphaFor(previousState.lastSampleNs, nowNs)
                    val nextValue = ewmaMovingAverage(previousState.value, current, alpha)
                    State(nextValue, nowNs)
                }
            }.value
    }

    private fun alphaFor(
        previousNs: Long,
        nowNs: Long,
    ): Double =
        when {
            previousNs == Long.MIN_VALUE -> 1.0
            nowNs <= previousNs -> 0.0
            else -> (1.0 - exp(-(nowNs - previousNs).toDouble() / smoothingTimeNs)).coerceIn(0.0, 1.0)
        }

    private data class State(
        val value: Double,
        val lastSampleNs: Long,
    )

    companion object {
        internal fun smoothingTimeNsForHalfLifeNs(halfLifeNs: Long) = (halfLifeNs.toDouble() / ln(2.0)).roundToLong()
    }
}
