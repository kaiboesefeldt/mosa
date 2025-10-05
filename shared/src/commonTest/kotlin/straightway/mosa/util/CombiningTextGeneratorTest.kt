package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CombiningTextGeneratorTest {
    @Test
    fun `size is product of parts sizes`() {
        val generator = anyOf("1", "2", "3") + anyOf("1", "2") + anyOf("1", "2", "3", "4", "5")
        assertEquals(3 * 2 * 5, generator.size)
    }

    @Test
    fun `iterator yields all combinations of parts`() {
        val generator = anyOf("a", "b") + "c" + anyOf("d", "e", "f")
        assertEquals(
            listOf("acd", "bcd", "ace", "bce", "acf", "bcf"),
            generator.toList()
        )
    }

    @Test
    fun `get uses provided joiner`() {
        val generator = (anyOf("foo" ) + "bar").joinBy { parts -> parts.joinToString("-") }
        val result = generator.first()
        assertEquals("foo-bar", result)
    }

    @Test
    fun `get gets item with index`() {
        val generator = anyOf("1", "2") +anyOf("a", "b", "c")
        val generated = (0L..5L).map { generator[it] }
        assertEquals(listOf("1a", "2a", "1b", "2b", "1c", "2c"), generated)
    }

    @Test
    fun `get with exceeding index throws IndexOutOfBoundsException`() {
        val generator = anyOf("1", "2") + anyOf("a", "b", "c")
        assertFailsWith(IndexOutOfBoundsException::class) { generator[6] }
    }
}
