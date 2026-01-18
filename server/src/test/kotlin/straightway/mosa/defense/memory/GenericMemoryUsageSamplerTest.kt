package straightway.mosa.defense.memory

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import kotlin.test.Test
import kotlin.test.assertEquals

class GenericMemoryUsageSamplerTest {
    @Test
    fun `is always available`() {
        val sampler = GenericMemoryUsageSampler { mockk<MemoryUsage>() }

        assertTrue(sampler.isAvailable)
    }

    @Test
    fun `uses heap usage if specified`() {
        val memoryBean =
            mockk<MemoryMXBean> {
                every { heapMemoryUsage } returns MemoryUsage(0L, 50L, 100L, 100L)
            }

        val sampler = GenericMemoryUsageSampler { memoryBean.heapMemoryUsage }

        assertEquals(0.5, sampler.sample(), 1e-6)
    }

    @Test
    fun `uses non-heap usage if specified`() {
        val memoryBean =
            mockk<MemoryMXBean> {
                every { nonHeapMemoryUsage } returns MemoryUsage(0L, 25L, 50L, -1L)
            }

        val sampler = GenericMemoryUsageSampler { memoryBean.nonHeapMemoryUsage }

        assertEquals(0.5, sampler.sample(), 1e-6)
    }
}
