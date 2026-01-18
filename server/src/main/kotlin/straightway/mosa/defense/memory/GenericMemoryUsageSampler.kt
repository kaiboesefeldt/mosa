package straightway.mosa.defense.memory

import java.lang.management.MemoryUsage
import kotlin.math.max

class GenericMemoryUsageSampler(
    private val sampleFn: () -> MemoryUsage,
) : MemoryUsageSampler {
    override val isAvailable = true

    override fun sample(): Double = totalUsageRatio()

    private fun totalUsageRatio(): Double {
        val usage = usageSlice(sampleFn()) ?: return Double.NaN
        return (usage.used / max(usage.limit, 1.0)).coerceIn(0.0, 10.0)
    }

    private fun usageSlice(usage: MemoryUsage): MemoryUsageSlice? {
        val limit =
            when {
                usage.max > 0L -> usage.max.toDouble()
                usage.committed > 0L -> usage.committed.toDouble()
                else -> 0.0
            }
        if (limit <= 0.0) return null
        val used = usage.used.toDouble().coerceAtLeast(0.0)
        return MemoryUsageSlice(used, limit)
    }
}
