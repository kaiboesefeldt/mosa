package straightway.mosa.defense.memory

import straightway.mosa.defense.core.SheddingDecider
import straightway.mosa.defense.core.SheddingRequest
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.util.Locale
import kotlin.math.max

class MemorySheddingDecider(
    private val config: MemorySheddingConfig,
    private val memorySampler: () -> Double = ProcessMemoryUsageGauge()::sample,
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
            "Memory usage above ${config.memorySheddingFull} (${formatPct(usage)})"
        } else {
            "Memory usage in shedding range ${config.memorySheddingStart}-${config.memorySheddingFull} (${formatPct(usage)})"
        }
}

class ProcessMemoryUsageGauge(
    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean(),
    private val bufferPools: List<BufferPoolMXBean> =
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java),
) {
    fun sample(): Double = totalUsageRatio()

    private fun totalUsageRatio(): Double {
        val heapUsage = usageSlice(memoryBean.heapMemoryUsage)
        val nonHeapUsage = usageSlice(memoryBean.nonHeapMemoryUsage)
        val bufferUsage =
            bufferPools
                .mapNotNull { bufferSlice(it) }

        val totalUsed = listOfNotNull(heapUsage, nonHeapUsage).sumOf { it.used } + bufferUsage.sumOf { it.used }
        val totalLimit = listOfNotNull(heapUsage, nonHeapUsage).sumOf { it.limit } + bufferUsage.sumOf { it.limit }

        if (totalLimit <= 0.0) return 0.0
        return (totalUsed / max(totalLimit, 1.0)).coerceIn(0.0, 10.0)
    }

    private fun usageSlice(usage: java.lang.management.MemoryUsage): UsageSlice? {
        val limit =
            when {
                usage.max > 0L -> usage.max.toDouble()
                usage.committed > 0L -> usage.committed.toDouble()
                else -> 0.0
            }
        if (limit <= 0.0) return null
        val used = usage.used.toDouble().coerceAtLeast(0.0)
        return UsageSlice(used, limit)
    }

    private fun bufferSlice(bufferPool: BufferPoolMXBean): UsageSlice? {
        val used = bufferPool.memoryUsed.toDouble().coerceAtLeast(0.0)
        val limit =
            when {
                bufferPool.totalCapacity > 0L -> bufferPool.totalCapacity.toDouble()
                used > 0.0 -> used
                else -> 0.0
            }
        if (limit <= 0.0) return null
        return UsageSlice(used, limit)
    }
}

private data class UsageSlice(
    val used: Double,
    val limit: Double,
)
