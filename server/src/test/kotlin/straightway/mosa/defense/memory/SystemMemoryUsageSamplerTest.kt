package straightway.mosa.defense.memory

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SystemMemoryUsageSamplerTest {
    @Test
    fun `uses memavailable instead of free memory`() {
        val sampler =
            SystemMemoryUsageSampler(
                totalMemoryBytes = { 1000L * 1024L },
                freeMemoryBytes = { 10L * 1024L },
                readTextFn = { path ->
                    when (path.toString()) {
                        "/proc/meminfo" ->
                            """
                            MemTotal:       1000 kB
                            MemAvailable:    400 kB
                            """.trimIndent()
                        else -> null
                    }
                },
                osName = "Linux",
            )

        assertEquals(0.6, sampler.sample(), 1e-6)
    }

    @Test
    fun `prefers cgroup v2 over meminfo`() {
        val sampler =
            SystemMemoryUsageSampler(
                totalMemoryBytes = { 1000L },
                freeMemoryBytes = { 10L },
                readTextFn = { path ->
                    when (path.toString()) {
                        "/sys/fs/cgroup/memory.current" -> "700"
                        "/sys/fs/cgroup/memory.max" -> "1000"
                        "/proc/meminfo" ->
                            """
                            MemAvailable: 900 kB
                            """.trimIndent()
                        else -> null
                    }
                },
                osName = "Linux",
            )

        assertEquals(0.7, sampler.sample(), 1e-6)
    }

    @Test
    fun `uses cgroup v1 when present`() {
        val sampler =
            SystemMemoryUsageSampler(
                totalMemoryBytes = { 1000L },
                freeMemoryBytes = { 10L },
                readTextFn = { path ->
                    when (path.toString()) {
                        "/sys/fs/cgroup/memory/memory.usage_in_bytes" -> "300"
                        "/sys/fs/cgroup/memory/memory.limit_in_bytes" -> "1000"
                        else -> null
                    }
                },
                osName = "Linux",
            )

        assertEquals(0.3, sampler.sample(), 1e-6)
    }

    @Test
    fun `falls back to free memory when cgroup and meminfo are unavailable`() {
        val sampler =
            SystemMemoryUsageSampler(
                totalMemoryBytes = { 1000L },
                freeMemoryBytes = { 200L },
                readTextFn = { _: Path -> null },
                osName = "Linux",
            )

        assertEquals(0.8, sampler.sample(), 1e-6)
    }

    @Test
    fun `uses stable metric name`() {
        val sampler = SystemMemoryUsageSampler(totalMemoryBytes = { 1000L }, freeMemoryBytes = { 500L })

        assertEquals("SystemMemory", sampler.toString())
    }
}
