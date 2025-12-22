package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals

class CombinedSheddingDeciderTest {
    @Test
    fun `returns maximum shedding result`() {
        val expectedResult = SheddingDecider.Result(0.9, "shed")

        val combined =
            CombinedSheddingDecider(
                listOf(
                    SheddingDecider { SheddingDecider.Result.NO_SHEDDING },
                    SheddingDecider { expectedResult },
                    SheddingDecider { SheddingDecider.Result(0.8, "shed not enough") },
                ),
            )

        val result = combined.shouldShed()

        assertEquals(expectedResult, result)
    }

    @Test
    fun `returns no shedding when all sub deciders do not shed`() {
        val combined =
            CombinedSheddingDecider(
                listOf(
                    SheddingDecider { SheddingDecider.Result.NO_SHEDDING },
                    SheddingDecider { SheddingDecider.Result.NO_SHEDDING },
                ),
            )

        val result = combined.shouldShed()

        assertEquals(SheddingDecider.Result.NO_SHEDDING, result)
    }
}
