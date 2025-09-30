package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RepeatingTextGeneratorTest {
    @Test
    fun `generate uses repetition count determined by stream`() {
        val generator = RepeatingTextGenerator(
            toRepeat = { bits -> if (bits.getNextBit() == 0) "a" else "b" },
            repetitions = { bits -> bits.getNextAsInt(2) + 1 }
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
            toRepeat = {
                val phrases = listOf("first", "second", "third")
                phrases[callCount++]
            },
            repetitions = { 2 },
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
            toRepeat = { ++calls; "unused" },
            repetitions = { 0 }
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("", result)
        assertEquals(0, calls)
    }
}
