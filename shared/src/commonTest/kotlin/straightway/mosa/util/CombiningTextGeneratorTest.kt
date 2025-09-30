package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CombiningTextGeneratorTest {
    @Test
    fun `generate combines child outputs`() {
        val generator = CombiningTextGenerator(
            TextGenerator { "alpha" },
            TextGenerator { "beta" },
            TextGenerator { "gamma" }
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("alphabetagamma", result)
    }

    @Test
    fun `generate uses provided joiner`() {
        val generator = CombiningTextGenerator(
            joiner = { parts -> parts.joinToString("-") },
            { "foo" },
            { "bar" }
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("foo-bar", result)
    }

    @Test
    fun `generate consumes shared bit stream for all parts`() {
        val generator = CombiningTextGenerator(
            parts = listOf(
                TextGenerator { bits -> if (bits.getNextBit() == 0) "A" else "B" },
                TextGenerator { bits -> if (bits.getNextBit() == 0) "C" else "D" }
            )
        )
        val bitStream = BitStream(byteArrayOf(0x01))

        val result = generator.generate(bitStream)

        assertEquals("BC", result)
        assertEquals(2, bitStream.consumedBits)
    }
}
