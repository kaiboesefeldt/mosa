package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatingTextGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is toRepeat numberOfPossibleOutputs times repetitions`() {
        val generator = RepeatingTextGenerator(
            FakeTextGenerator(numberOfPossibleOutputs = 3),
            repetitions = 2..3
        )
        assertEquals(2 * 3 + 3 * 3, generator.numberOfPossibleOutputs)
    }

    @Test
    fun `allPossibleOutputs yields repeated combinations`() {
        val generator = RepeatingTextGenerator(
            FixedOptionsTextGenerator("a", "b"),
            repetitions = 2..3
        )
        assertEquals(
            listOf("aa", "ab", "ba", "bb", "aaa", "aab", "aba", "abb", "baa", "bab", "bba", "bbb"),
            generator.allPossibleOutputs.toList()
        )
    }

    @Test
    fun `generate uses repetition count determined by stream`() {
        val generator = RepeatingTextGenerator(
            toRepeat = FixedOptionsTextGenerator("a", "b" ),
            repetitions = 1..4
        )
        val bitStream = BitStream(byteArrayOf(0x15))

        val result = generator.generate(bitStream)

        assertEquals("bab", result)
        assertEquals(5, bitStream.consumedBits)
    }

    @Test
    fun `generate applies custom joiner`() {
        var callCount = 0
        val generator = RepeatingTextGenerator(
            toRepeat = FakeTextGenerator {
                val phrases = listOf("first", "second", "third")
                phrases[callCount++]
            },
            repetitions = 2..2,
            joiner = { parts -> parts.joinToString(" | ") }
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("first | second", result)
        assertEquals(2, callCount)
    }

    @Test
    fun `generate with zero repetitions skips inner generator`() {
        var calls = 0
        val generator = RepeatingTextGenerator(
            toRepeat = FakeTextGenerator {
                ++calls
                "unused"
            },
            repetitions = 0..0
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("", result)
        assertEquals(0, calls)
    }
}
