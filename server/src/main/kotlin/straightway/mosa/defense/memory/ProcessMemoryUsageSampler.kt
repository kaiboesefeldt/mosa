package straightway.mosa.defense.memory

import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import kotlin.math.max

class ProcessMemoryUsageSampler(
    memoryBean: MemoryMXBean? = ManagementFactory.getMemoryMXBean(),
) : MemoryUsageSampler {
    private val memoryBean by lazy { memoryBean!! }
    override val isAvailable: Boolean =
        memoryBean?.run {
            heapMemoryUsage != null && nonHeapMemoryUsage != null
        } ?: false

    override fun sample(): Double = totalUsageRatio()

    private fun totalUsageRatio(): Double {
        val heapUsage = usageSlice(memoryBean.heapMemoryUsage)
        val nonHeapUsage = usageSlice(memoryBean.nonHeapMemoryUsage)

        val totalUsed = listOfNotNull(heapUsage, nonHeapUsage).sumOf { it.used }
        val totalLimit = listOfNotNull(heapUsage, nonHeapUsage).sumOf { it.limit }

        if (totalLimit <= 0.0) return 0.0
        return (totalUsed / max(totalLimit, 1.0)).coerceIn(0.0, 10.0)
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
