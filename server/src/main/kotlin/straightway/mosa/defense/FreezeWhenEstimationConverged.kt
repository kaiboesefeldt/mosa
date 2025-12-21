package straightway.mosa.defense

import kotlin.math.abs
import kotlin.math.max

class FreezeWhenEstimationConverged(
    val raw: Estimator,
    val chunkSize: Int = 50,
    val stabilityFactor: Double = 0.01,
    val epsilon: Double = 1e-6,
) : Estimator by raw {
    private var lastChunkAverage: Double = Double.NaN
    private var currChunkSum: Double = 0.0
    private var currChunkSamples: Int = 0
    private var stableValue: Double? = null

    init {
        require(chunkSize > 0) { "chunkSize must be positive" }
        require(stabilityFactor > 0.0) { "stabilityFactor must be positive" }
    }

    override fun add(sample: Double) {
        if (stableValue != null) return

        raw.add(sample)

        raw.estimate().let {
            if (it.isFinite()) {
                currChunkSum += it
                ++currChunkSamples

                if (chunkSize <= currChunkSamples) {
                    val chunkAverage = currChunkSum / chunkSize
                    val deviation = abs(lastChunkAverage - chunkAverage)
                    val tolerance = max(epsilon, abs(chunkAverage * stabilityFactor))
                    if (deviation <= tolerance) {
                        stableValue = chunkAverage
                    } else {
                        lastChunkAverage = chunkAverage
                        currChunkSum = 0.0
                        currChunkSamples = 0
                    }
                }
            }
        }
    }

    override fun estimate(): Double = stableValue ?: Double.NaN
}
