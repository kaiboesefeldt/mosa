package straightway.mosa.defense.memory

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.Locale
import kotlin.math.max

class MemorySheddingDecider(
    private val memorySheddingThreshold: Double,
    private val memorySampler: () -> Double = HeapUsageGauge()::sample,
) : SheddingDecider {
    override suspend fun decideShedding(request: SheddingRequest) =
        memorySampler()
            .let { if (it.isFinite()) it.coerceAtLeast(0.0) else 0.0 }
            .let { usage ->
                if (memorySheddingThreshold <= usage) {
                    SheddingDecider.Result(
                        sheddingFactor = 1.0,
                        reason = "Heap usage above $memorySheddingThreshold (${formatPct(usage)})",
                    )
                } else {
                    SheddingDecider.Result.NO_SHEDDING
                }
            }

    private fun formatPct(v: Double): String = "${"%.1f".format(Locale.US, v * 100)}%"
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
