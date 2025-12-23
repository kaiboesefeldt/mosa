package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals

class SheddingStateTest {
    @Test
    fun `defaults to zero factor and no shedding`() {
        val state = SheddingState()

        assertEquals(0.0, state.smoothedSheddingFactor)
        assertEquals(SheddingDecider.Result.NO_SHEDDING, state.cachedDecision)
    }
}
