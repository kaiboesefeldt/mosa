package straightway.mosa.defense.memory

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.util.Locale

class MemorySheddingDecider(
    private val config: MemorySheddingConfig,
    private val memorySampler: MemoryUsageSampler,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest) =
        if (memorySampler.isAvailable) {
            normalizedUsage().let { usage ->
                val sheddingFactor = sheddingFactorFor(usage)
                if (sheddingFactor <= 0.0) {
                    SheddingDecider.Result.NO_SHEDDING
                } else {
                    SheddingDecider.Result(
                        sheddingFactor = sheddingFactor,
                        reason = sheddingReason(usage),
                    )
                }
            }
        } else {
            SheddingDecider.Result.NO_SHEDDING
        }

    private fun formatPct(v: Double): String = "${"%.1f".format(Locale.US, v * 100)}%"

    private fun normalizedUsage(): Double =
        memorySampler
            .sample()
            .let { if (it.isFinite()) it else 0.0 }
            .coerceAtLeast(0.0)

    private fun sheddingFactorFor(usage: Double): Double =
        when {
            usage < config.memorySheddingStart -> 0.0
            config.memorySheddingFull <= usage -> 1.0
            else ->
                ((usage - config.memorySheddingStart) / (config.memorySheddingFull - config.memorySheddingStart))
                    .coerceIn(0.0, 1.0)
        }

    private fun sheddingReason(usage: Double): String =
        if (config.memorySheddingFull <= usage) {
            "Memory usage above ${config.memorySheddingFull} (${formatPct(usage)})"
        } else {
            "Memory usage in shedding range ${config.memorySheddingStart}-${config.memorySheddingFull} (${formatPct(usage)})"
        }
}
