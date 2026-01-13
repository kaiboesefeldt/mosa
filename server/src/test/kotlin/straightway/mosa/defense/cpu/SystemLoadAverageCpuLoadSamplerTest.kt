package straightway.mosa.defense.cpu

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.lang.management.ManagementFactory
import java.lang.management.OperatingSystemMXBean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemLoadAverageCpuLoadSamplerTest {
    private val osBean: OperatingSystemMXBean = mockk()

    @BeforeTest
    fun setUp() {
        mockkStatic(ManagementFactory::class)
        every { ManagementFactory.getOperatingSystemMXBean() } returns osBean
    }

    @AfterTest
    fun tearDown() {
        clearMocks(osBean)
        unmockkStatic(ManagementFactory::class)
    }

    @Test
    fun `samples load average from mx bean`() {
        every { osBean.systemLoadAverage } returns 0.42

        val sampler = SystemLoadAverageCpuLoadSampler()

        assertEquals(0.42, sampler.sample())
    }

    @Test
    fun `is operational when load average is finite and non-negative`() {
        every { osBean.systemLoadAverage } returns 0.0

        val sampler = SystemLoadAverageCpuLoadSampler()

        assertTrue(sampler.isOperational)
    }

    @Test
    fun `is not operational when load average is negative`() {
        every { osBean.systemLoadAverage } returns -0.1

        val sampler = SystemLoadAverageCpuLoadSampler()

        assertFalse(sampler.isOperational)
    }

    @Test
    fun `is not operational when load average is nan`() {
        every { osBean.systemLoadAverage } returns Double.NaN

        val sampler = SystemLoadAverageCpuLoadSampler()

        assertFalse(sampler.isOperational)
    }

    @Test
    fun `is not operational when load average is infinite`() {
        every { osBean.systemLoadAverage } returns Double.POSITIVE_INFINITY

        val sampler = SystemLoadAverageCpuLoadSampler()

        assertFalse(sampler.isOperational)
    }
}
