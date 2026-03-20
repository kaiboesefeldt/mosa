package straightway.mosa.defense.memory

import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType

class NonHeapMemoryPoolUsageSampler(
    private val sampleFn: () -> List<MemoryPoolMXBean>,
) : MemoryUsageSampler {
    private var lastPoolName = "NonHeapMemory"

    override val isAvailable: Boolean
        get() = boundedNonHeapPools().isNotEmpty()

    override fun sample(): Double {
        val mostUtilizedPool =
            boundedNonHeapPools()
                .maxByOrNull { (_, usageRatio) -> usageRatio }
                ?: return Double.NaN
        lastPoolName = "NonHeapMemory(${mostUtilizedPool.first})"
        return mostUtilizedPool.second
    }

    override fun toString() = lastPoolName

    private fun boundedNonHeapPools(): List<Pair<String, Double>> =
        sampleFn()
            .asSequence()
            .filter { it.type == MemoryType.NON_HEAP }
            .mapNotNull { pool ->
                pool.usage
                    ?.takeIf { usage -> 0L < usage.max }
                    ?.let { usage ->
                        pool.name to (usage.used.toDouble() / usage.max.toDouble()).coerceIn(0.0, 10.0)
                    }
            }.toList()
}
