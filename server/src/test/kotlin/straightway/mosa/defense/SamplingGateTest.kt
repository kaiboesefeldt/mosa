package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SamplingGateTest {
    @Test
    fun `returns true when interval not elapsed`() {
        val gate = SamplingGate(lastSampleNs = 1_000L)

        assertTrue(gate.isWithinSamplingInterval(1_050L, 100L))
    }

    @Test
    fun `returns false when interval elapsed`() {
        val gate = SamplingGate(lastSampleNs = 1_000L)

        assertFalse(gate.isWithinSamplingInterval(1_100L, 100L))
    }
}
