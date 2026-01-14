package straightway.mosa.defense.cpu

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.HardwareAbstractionLayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OshiCpuLoadSamplerTest {
    @Test
    fun `samples on first call using initial ticks`() {
        val firstTicks = longArrayOf(1, 2, 3)
        val secondTicks = longArrayOf(2, 3, 4)

        withMockedProcessor(
            ticks = listOf(firstTicks, secondTicks),
            load = 0.42,
        ) { capturedTicks ->
            val sampler = OshiCpuLoadSampler()

            assertEquals(0.42, sampler.sample())
            assertEquals(firstTicks, capturedTicks.captured)
        }
    }

    @Test
    fun `is operational when load is finite and non-negative`() {
        withMockedProcessor(load = 0.0) {
            val sampler = OshiCpuLoadSampler()

            assertTrue(sampler.isOperational)
        }
    }

    @Test
    fun `is not operational when load is negative`() {
        withMockedProcessor(load = -0.1) {
            val sampler = OshiCpuLoadSampler()

            assertFalse(sampler.isOperational)
        }
    }

    @Test
    fun `is not operational when load is nan`() {
        withMockedProcessor(load = Double.NaN) {
            val sampler = OshiCpuLoadSampler()

            assertFalse(sampler.isOperational)
        }
    }

    @Test
    fun `is not operational when load is infinite`() {
        withMockedProcessor(load = Double.POSITIVE_INFINITY) {
            val sampler = OshiCpuLoadSampler()

            assertFalse(sampler.isOperational)
        }
    }

    @Test
    fun `is not operational when processor throws`() {
        withFailingSystemInfo {
            val sampler = OshiCpuLoadSampler()

            assertFalse(sampler.isOperational)
        }
    }

    @Test
    fun `is not operational when hardware processor lookup throws`() {
        mockkConstructor(SystemInfo::class) {
            val hardware = mockk<HardwareAbstractionLayer>()
            every { SystemInfo().hardware } returns hardware
            every { hardware.processor } throws IllegalStateException("processor failure")

            val sampler1 = OshiCpuLoadSampler()
            assertFalse(sampler1.isOperational)
            assertTrue(sampler1.sample().isNaN())
        }
    }

    @Test
    fun `returns nan when system cpu load ticks throws`() {
        val processor = mockk<CentralProcessor>()
        every { processor.systemCpuLoadTicks } throws IllegalStateException("tick failure")
        every { processor.getSystemCpuLoadBetweenTicks(any()) } returns 0.12
        withMockedSystemInfo(processor) {
            val sampler1 = OshiCpuLoadSampler()
            assertFalse(sampler1.isOperational)
            assertTrue(sampler1.sample().isNaN())
        }
    }

    private fun withMockedProcessor(
        ticks: List<LongArray> = listOf(longArrayOf(1), longArrayOf(2)),
        load: Double,
        block: (CapturingSlot<LongArray>) -> Unit,
    ) {
        val processor = mockk<CentralProcessor>()
        var tickIndex = 0
        val capturedTicks = slot<LongArray>()

        every { processor.systemCpuLoadTicks } answers {
            ticks.getOrElse(tickIndex++) { ticks.last() }
        }
        every { processor.getSystemCpuLoadBetweenTicks(capture(capturedTicks)) } returns load

        withMockedSystemInfo(processor) {
            block(capturedTicks)
        }
    }

    private fun withMockedSystemInfo(
        processor: CentralProcessor,
        block: () -> Unit,
    ) {
        mockkConstructor(SystemInfo::class) {
            val hardware = mockk<HardwareAbstractionLayer>()
            every { SystemInfo().hardware } returns hardware
            every { hardware.processor } returns processor

            block()
        }
    }

    private fun withFailingSystemInfo(block: () -> Unit) {
        mockkConstructor(SystemInfo::class) {
            every { SystemInfo().hardware } throws IllegalStateException("hardware failure")

            block()
        }
    }
}
