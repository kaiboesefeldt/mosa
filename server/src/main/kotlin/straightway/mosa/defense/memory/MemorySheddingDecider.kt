package straightway.mosa.defense.memory

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.Locale
import kotlin.math.max

class MemorySheddingDecider(
    private val config: MemorySheddingConfig,
    private val memorySampler: () -> Double = HeapUsageGauge()::sample,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest) =
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

    private fun formatPct(v: Double): String = "${"%.1f".format(Locale.US, v * 100)}%"

    private fun normalizedUsage(): Double =
        memorySampler()
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
            "Heap usage above ${config.memorySheddingFull} (${formatPct(usage)})"
        } else {
            "Heap usage in shedding range ${config.memorySheddingStart}-${config.memorySheddingFull} (${formatPct(usage)})"
        }
}

class HeapUsageGauge(
    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean(),
) {
    fun sample(): Double = heapUsageRatio()

    private fun heapUsageRatio(): Double {
        val heap = memoryBean.heapMemoryUsage
        val used = heap.used.toDouble().coerceAtLeast(0.0)

        val maxOrCommitted =
            when {
                heap.max > 0L -> heap.max.toDouble()
                heap.committed > 0L -> heap.committed.toDouble()
                else -> return 0.0
            }

        return (used / max(maxOrCommitted, 1.0)).coerceIn(0.0, 10.0)
    }
}
