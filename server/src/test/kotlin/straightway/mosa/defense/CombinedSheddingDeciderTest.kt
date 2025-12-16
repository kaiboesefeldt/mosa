package straightway.mosa.defense

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class CombinedSheddingDeciderTest {
    @Test
    fun `returns first shedding result and short-circuits`() {
        val calls = mutableListOf<String>()
        val expectedResult = SheddingDecider.Result(true, "shed")

        val combined =
            CombinedSheddingDecider(
                listOf(
                    SheddingDecider {
                        calls.add("first")
                        SheddingDecider.Result(false, "")
                    },
                    SheddingDecider {
                        calls.add("second")
                        expectedResult
                    },
                    SheddingDecider {
                        fail("The last sub-provider should not be called")
                    },
                ),
            )

        val result = combined.shouldShed()

        assertTrue(result.shouldShed)
        assertEquals("shed", result.reason)
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun `returns no shedding when all sub deciders do not shed`() {
        val combined =
            CombinedSheddingDecider(
                listOf(
                    SheddingDecider { SheddingDecider.Result(false, "ignored") },
                    SheddingDecider { SheddingDecider.Result(false, "") },
                ),
            )

        val result = combined.shouldShed()

        assertFalse(result.shouldShed)
        assertEquals("", result.reason)
    }
}
