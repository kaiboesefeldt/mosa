package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class FixedOptionsTextGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is number of options`() {
        val generator = FixedOptionsTextGenerator(listOf("alpha", "beta"))
        assertEquals(2, generator.numberOfPossibleOutputs)
    }

    @Test
    fun `allPossibleOutputs yields fixed options`() {
        val textOptions = listOf("alpha", "beta")
        val generator = FixedOptionsTextGenerator(textOptions)
        assertEquals(textOptions, generator.allPossibleOutputs.toList())
    }

    @Test
    fun `generate returns first option when stream selects index 0`() {
        val generator = FixedOptionsTextGenerator(listOf("alpha", "beta"))
        val bitStream = BitStream(byteArrayOf(0x00))

        val result = generator.generate(bitStream)

        assertEquals("alpha", result)
        assertEquals(1, bitStream.consumedBits)
    }

    @Test
    fun `generate returns last option when stream selects highest index`() {
        val generator = FixedOptionsTextGenerator(listOf("red", "green", "blue"))
        val bitStream = BitStream(byteArrayOf(0x01))

        val result = generator.generate(bitStream)

        assertEquals("blue", result)
        assertEquals(2, bitStream.consumedBits)
    }

    @Test
    fun `generate ignores indices that exceed max`() {
        val generator = FixedOptionsTextGenerator(listOf("alpha", "beta", "gamma"))
        val bitStream = BitStream(byteArrayOf(0x07))

        val result = generator.generate(bitStream)

        assertEquals("gamma", result)
        assertEquals(4, bitStream.consumedBits)
    }

    @Test
    fun `invoke vararg factory creates generator from options`() {
        val generator = FixedOptionsTextGenerator("left", "right")
        val bitStream = BitStream(byteArrayOf(0x01))

        val result = generator.generate(bitStream)

        assertEquals("right", result)
    }
}
