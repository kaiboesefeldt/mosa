package straightway.mosa.defense.memory

import io.mockk.every
import io.mockk.mockk
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType
import java.lang.management.MemoryUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NonHeapMemoryPoolUsageSamplerTest {
    @Test
    fun `is unavailable when no bounded non-heap pool exists`() {
        val sampler =
            NonHeapMemoryPoolUsageSampler {
                listOf(
                    memoryPool("Metaspace", MemoryType.NON_HEAP, MemoryUsage(0L, 80L, 100L, -1L)),
                    memoryPool("G1 Eden Space", MemoryType.HEAP, MemoryUsage(0L, 50L, 100L, 100L)),
                )
            }

        assertFalse(sampler.isAvailable)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `samples the most utilized bounded non-heap pool`() {
        val sampler =
            NonHeapMemoryPoolUsageSampler {
                listOf(
                    memoryPool("Metaspace", MemoryType.NON_HEAP, MemoryUsage(0L, 80L, 100L, 100L)),
                    memoryPool("CodeHeap 'profiled nmethods'", MemoryType.NON_HEAP, MemoryUsage(0L, 45L, 50L, 50L)),
                    memoryPool("G1 Old Gen", MemoryType.HEAP, MemoryUsage(0L, 99L, 100L, 100L)),
                )
            }

        assertTrue(sampler.isAvailable)
        assertEquals(0.9, sampler.sample(), 1e-6)
        assertEquals("NonHeapMemory(CodeHeap 'profiled nmethods')", sampler.toString())
    }

    private fun memoryPool(
        name: String,
        type: MemoryType,
        usage: MemoryUsage?,
    ): MemoryPoolMXBean =
        mockk {
            every { this@mockk.name } returns name
            every { this@mockk.type } returns type
            every { this@mockk.usage } returns usage
        }
}
