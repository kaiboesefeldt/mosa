package straightway.mosa.defense.cpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CpuSheddingConfigTest {
    @Test
    fun `rejects negative load shedding start`() {
        assertFailsWith<IllegalArgumentException> {
            CpuSheddingConfig(
                loadSheddingStart = -0.1,
                loadSheddingFull = 0.5,
            )
        }
    }

    @Test
    fun `rejects full threshold below start threshold`() {
        assertFailsWith<IllegalArgumentException> {
            CpuSheddingConfig(
                loadSheddingStart = 0.5,
                loadSheddingFull = 0.4,
            )
        }
    }

    @Test
    fun `accepts start and full thresholds`() {
        val config =
            CpuSheddingConfig(
                loadSheddingStart = 0.2,
                loadSheddingFull = 0.8,
            )

        assertEquals(0.2, config.loadSheddingStart)
        assertEquals(0.8, config.loadSheddingFull)
    }
}
