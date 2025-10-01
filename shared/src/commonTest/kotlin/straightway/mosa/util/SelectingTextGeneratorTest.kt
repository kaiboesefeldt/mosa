package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectingTextGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is sum of options`() {
        val generator = SelectingTextGenerator(
            1 to FakeTextGenerator(numberOfPossibleOutputs = 3),
            2 to FakeTextGenerator(numberOfPossibleOutputs = 2),
        )
        assertEquals(2 + 3, generator.numberOfPossibleOutputs)
    }

    @Test
    fun `allPossibleOutputs yields union of all options`() {
        val generator = SelectingTextGenerator(
            1 to FixedOptionsTextGenerator("a", "b"),
            2 to FixedOptionsTextGenerator("c", "d", "e"),
        )
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            generator.allPossibleOutputs.toList()
        )
    }

        @Test
    fun `generate selects option whose weight range covers selector`() {
        var firstCalls = 0
        var secondCalls = 0
        val generator = SelectingTextGenerator(
            listOf(
                2 to FakeTextGenerator { ++firstCalls; "first" },
                3 to FakeTextGenerator { ++secondCalls; "second" }
            )
        )

        val result = generator.generate(BitStream(byteArrayOf(0x06)))

        assertEquals("second", result)
        assertEquals(0, firstCalls)
        assertEquals(1, secondCalls)
    }

    @Test
    fun `generate passes remaining bits to chosen generator`() {
        val generator = SelectingTextGenerator(
            listOf(
                1 to FakeTextGenerator { bits -> "x${bits.getNextBit()}" },
                1 to FakeTextGenerator { bits -> "y${bits.getNextBit()}" }
            )
        )
        val bitStream = BitStream(byteArrayOf(0x04))

        val result = generator.generate(bitStream)

        assertEquals("x1", result)
        assertEquals(3, bitStream.consumedBits)
    }

    @Test
    fun `generate with zero total weight returns first option without consuming bits`() {
        var calls = 0
        val generator = SelectingTextGenerator(
            listOf(0 to FakeTextGenerator { ++calls; "only" })
        )
        val bitStream = BitStream(byteArrayOf(0xFF.toByte()))

        val result = generator.generate(bitStream)

        assertEquals("only", result)
        assertEquals(1, calls)
        assertEquals(0, bitStream.consumedBits)
    }

    @Test
    fun `generate stops at last option when selector stays above sum`() {
        var firstCalls = 0
        var secondCalls = 0
        var thirdCalls = 0
        val generator = SelectingTextGenerator(
            listOf(
                1 to FakeTextGenerator { ++firstCalls; "first" },
                1 to FakeTextGenerator { ++secondCalls; "second" },
                5 to FakeTextGenerator { bits ->
                    ++thirdCalls
                    "third${bits.getNextBit()}"
                }
            )
        )
        val bitStream = BitStream(byteArrayOf(0x05))

        val result = generator.generate(bitStream)

        assertEquals("third0", result)
        assertEquals(0, firstCalls)
        assertEquals(0, secondCalls)
        assertEquals(1, thirdCalls)
        assertEquals(4, bitStream.consumedBits)
    }

    @Test
    fun `invoke vararg factory creates generator from weights`() {
        val generator = SelectingTextGenerator(
            1 to FakeTextGenerator { "first" },
            1 to FakeTextGenerator { "second" }
        )

        val result = generator.generate(BitStream(byteArrayOf(0x01)))

        assertEquals("second", result)
    }
}
