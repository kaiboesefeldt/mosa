package straightway.mosa.defense.memory

import io.mockk.every
import io.mockk.mockk
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProcessMemoryUsageSamplerTest {
    @Test
    fun `reports unavailable when memory bean is missing`() {
        val sampler = ProcessMemoryUsageSampler(null)

        assertFalse(sampler.isAvailable)
    }

    @Test
    fun `combines heap and non-heap usage`() {
        val memoryBean =
            mockk<MemoryMXBean> {
                every { heapMemoryUsage } returns MemoryUsage(0L, 50L, 100L, 100L)
                every { nonHeapMemoryUsage } returns MemoryUsage(0L, 25L, 50L, -1L)
            }

        val sampler = ProcessMemoryUsageSampler(memoryBean)

        assertEquals(0.5, sampler.sample(), 1e-6)
    }
}
