package straightway.mosa.defense.requestduration

import straightway.mosa.defense.core.SheddingDecider
import kotlin.math.pow

class RequestDurationSheddingState(
    private val config: RequestDurationSheddingConfig,
    @Volatile var _smoothedSheddingFactor: Double = 0.0,
) {
    @Volatile
    private var _cachedDecision: SheddingDecider.Result = SheddingDecider.Result.NO_SHEDDING

    val smoothedSheddingFactor get() = _smoothedSheddingFactor
    val cachedDecision get() = _cachedDecision

    fun cacheDecision(decision: SheddingDecider.Result) {
        _cachedDecision = decision
    }

    fun decayWithoutNewSample(steps: Long) {
        if (steps <= 0L) return
        val decayFactor = (1.0 - config.sheddingDecayAlpha).pow(steps.toDouble())
        _smoothedSheddingFactor *= decayFactor
    }

    fun addSample(sample: Double) {
        _smoothedSheddingFactor =
            ewmaMovingAverage(_smoothedSheddingFactor, sample, config.sheddingSampleAlpha)
    }
}
