package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CombiningTextGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is product of parts numberOfPossibleOutputs`() {
        val generator = CombiningTextGenerator(
            FakeTextGenerator(numberOfPossibleOutputs = 3),
            FakeTextGenerator(numberOfPossibleOutputs = 2),
            FakeTextGenerator(numberOfPossibleOutputs = 5),
        )
        assertEquals(30, generator.numberOfPossibleOutputs)
    }

    @Test
    fun `allPossibleOutputs yields all combinations of parts`() {
        val generator = CombiningTextGenerator(
            FixedOptionsTextGenerator("a", "b"),
            FixedOptionsTextGenerator("c"),
            FixedOptionsTextGenerator("d", "e", "f"),
        )
        assertEquals(
            listOf("acd", "ace", "acf", "bcd", "bce", "bcf"),
            generator.allPossibleOutputs.toList()
        )
    }

    @Test
    fun `generate combines child outputs`() {
        val generator = CombiningTextGenerator(
            FixedOptionsTextGenerator("alpha"),
            FixedOptionsTextGenerator("beta"),
            FixedOptionsTextGenerator("gamma")
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("alphabetagamma", result)
    }

    @Test
    fun `generate uses provided joiner`() {
        val generator = CombiningTextGenerator(
            joiner = { parts -> parts.joinToString("-") },
            FixedOptionsTextGenerator("foo" ),
            FixedOptionsTextGenerator("bar")
        )

        val result = generator.generate(BitStream(byteArrayOf()))

        assertEquals("foo-bar", result)
    }

    @Test
    fun `generate consumes shared bit stream for all parts`() {
        val generator = CombiningTextGenerator(
            parts = listOf(
                FixedOptionsTextGenerator("A", "B"),
                FixedOptionsTextGenerator("C", "D"),
            )
        )
        val bitStream = BitStream(byteArrayOf(0x01))

        val result = generator.generate(bitStream)

        assertEquals("BC", result)
        assertEquals(2, bitStream.consumedBits)
    }
}
