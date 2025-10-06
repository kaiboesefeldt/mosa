package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FixedOptionsGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is number of options`() {
        val generator = anyOf("alpha", "beta")
        assertEquals(2, generator.size)
    }

    @Test
    fun `iterator yields fixed options`() {
        val textOptions = listOf("alpha", "beta")
        val generator = FixedOptionsGenerator(textOptions)
        assertEquals(textOptions, generator.toList())
    }

    @Test
    fun `get gets item with index`() {
        val generator = anyOf("0", "1", "2")
        assertEquals("1", generator[1])
    }

    @Test
    fun `get with exceeding index throws IndexOutOfBoundsException`() {
        val generator = anyOf("0", "1", "2")
        assertFailsWith(IndexOutOfBoundsException::class) { generator[3] }
    }
}
