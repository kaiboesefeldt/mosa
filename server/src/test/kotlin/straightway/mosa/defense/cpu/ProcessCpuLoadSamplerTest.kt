package straightway.mosa.defense.cpu

import com.sun.management.OperatingSystemMXBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessCpuLoadSamplerTest {
    private val originalOsMxBean = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
    private var sample: Double = Double.NaN

    @BeforeEach
    fun setUp() {
        mockkStatic(ManagementFactory::class)
        every {
            ManagementFactory
                .getOperatingSystemMXBean()
        } returns createFakeOsMxBean()
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(ManagementFactory::class)
        sample = Double.NaN
    }

    @Test
    fun `samples process cpu load from provider`() {
        val sampler = ProcessCpuLoadSampler()
        sample = 0.42

        assertEquals(0.42, sampler.sample())
    }

    @Test
    fun `is operational when process cpu load is finite and non-negative`() {
        val sampler = ProcessCpuLoadSampler()
        sample = 0.0

        assertTrue(sampler.isOperational)
    }

    @Test
    fun `is not operational when process cpu load is negative`() {
        val sampler = ProcessCpuLoadSampler()
        sample = -1.0

        assertFalse(sampler.isOperational)
    }

    @Test
    fun `is not operational when process cpu load is not a number`() {
        val sampler = ProcessCpuLoadSampler()
        sample = Double.NaN

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `is not operational if OperatingSystemMxBean is null`() {
        every { ManagementFactory.getOperatingSystemMXBean() } returns null
        sample = 0.5
        val sampler = ProcessCpuLoadSampler()

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Test
    fun `is not operational if OperatingSystemMxBean has another type`() {
        every { ManagementFactory.getOperatingSystemMXBean() } returns mockk(relaxed = true)
        sample = 0.5
        val sampler = ProcessCpuLoadSampler()

        assertFalse(sampler.isOperational)
        assertTrue(sampler.sample().isNaN())
    }

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    private fun createFakeOsMxBean(): OperatingSystemMXBean =
        object : OperatingSystemMXBean by originalOsMxBean {
            override fun getProcessCpuLoad() = sample
        }
}
