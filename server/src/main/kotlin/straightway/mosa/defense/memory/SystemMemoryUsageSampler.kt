package straightway.mosa.defense.memory

import com.sun.management.OperatingSystemMXBean
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.math.max

class SystemMemoryUsageSampler(
    private val totalMemoryBytes: () -> Long,
    private val freeMemoryBytes: () -> Long,
    private val readTextFn: (Path) -> String? = ::defaultReadText,
    private val osName: String = System.getProperty("os.name", ""),
) : MemoryUsageSampler {
    constructor(
        osBean: OperatingSystemMXBean,
        readTextFn: (Path) -> String? = ::defaultReadText,
        osName: String = System.getProperty("os.name", ""),
    ) : this(
        totalMemoryBytes = { osBean.totalMemorySize },
        freeMemoryBytes = { osBean.freeMemorySize },
        readTextFn = readTextFn,
        osName = osName,
    )

    override val isAvailable: Boolean = true

    override fun sample(): Double {
        val total = totalMemoryBytes().toDouble()
        if (total <= 0.0) return Double.NaN

        val available =
            (
                cgroupAvailableBytes()
                    ?: memInfoAvailableBytes()
                    ?: freeMemoryBytes().toDouble()
            ).coerceIn(0.0, total)
        val used = (total - available).coerceAtLeast(0.0)
        return (used / max(total, 1.0)).coerceIn(0.0, 10.0)
    }

    override fun toString(): String = "SystemMemory"

    private fun cgroupAvailableBytes(): Double? =
        cgroupV2AvailableBytes()
            ?: cgroupV1AvailableBytes()

    private fun cgroupV2AvailableBytes(): Double? {
        val current =
            readLongFile("/sys/fs/cgroup/memory.current")
                ?: return null
        val limitRaw =
            readTextFile("/sys/fs/cgroup/memory.max")
                ?.trim()
                ?: return null
        val limit =
            when (limitRaw) {
                "max" -> return null
                else -> limitRaw.toLongOrNull() ?: return null
            }
        if (limit <= 0L || isLikelyUnlimited(limit)) return null
        return (limit - current).coerceAtLeast(0L).toDouble()
    }

    private fun cgroupV1AvailableBytes(): Double? {
        val current =
            readLongFile("/sys/fs/cgroup/memory/memory.usage_in_bytes")
                ?: return null
        val limit =
            readLongFile("/sys/fs/cgroup/memory/memory.limit_in_bytes")
                ?: return null
        if (limit <= 0L || isLikelyUnlimited(limit)) return null
        return (limit - current).coerceAtLeast(0L).toDouble()
    }

    private fun isLikelyUnlimited(limit: Long): Boolean = limit >= (1L shl 60)

    private fun memInfoAvailableBytes(): Double? {
        if (!osName.startsWith("Linux", ignoreCase = true)) return null
        val memInfo = readTextFile("/proc/meminfo") ?: return null
        val line =
            memInfo
                .lineSequence()
                .firstOrNull { it.startsWith("MemAvailable:") }
                ?: return null
        val availableKb =
            line
                .substringAfter(':', "")
                .trim()
                .substringBefore(' ')
                .toLongOrNull()
                ?: return null
        return availableKb * 1024.0
    }

    private fun readLongFile(path: String): Long? =
        readTextFile(path)
            ?.trim()
            ?.toLongOrNull()

    private fun readTextFile(path: String): String? = readTextFn(Path.of(path))
}

private fun defaultReadText(path: Path): String? = runCatching { path.readText() }.getOrNull()
