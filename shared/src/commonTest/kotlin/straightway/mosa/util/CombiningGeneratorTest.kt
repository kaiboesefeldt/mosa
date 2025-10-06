package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CombiningGeneratorTest {
    @Test
    fun `size is product of parts sizes`() {
        val generator = concat(anyOf("1", "2", "3"), anyOf("1", "2"), anyOf("1", "2", "3", "4", "5"))
        assertEquals(3 * 2 * 5, generator.size)
    }

    @Test
    fun `iterator yields all combinations of parts`() {
        val generator = concat(anyOf("a", "b"), anyOf("c"), anyOf("d", "e", "f")).trans { it.joinToString("") }
        assertEquals(
            listOf("acd", "bcd", "ace", "bce", "acf", "bcf"),
            generator.toList()
        )
    }

    @Test
    fun `get uses provided joiner`() {
        val generator = stringGen(concat(anyOf("foo" ), anyOf("bar")), "-")
        val result = generator.first()
        assertEquals("foo-bar", result)
    }

    @Test
    fun `get gets item with index`() {
        val generator = stringGen(concat(anyOf("1", "2"), anyOf("a", "b", "c")))
        val generated = (0L..5L).map { generator[it] }
        assertEquals(listOf("1a", "2a", "1b", "2b", "1c", "2c"), generated)
    }

    @Test
    fun `get with exceeding index throws IndexOutOfBoundsException`() {
        val generator = concat(anyOf("1", "2"), anyOf("a", "b", "c")).trans { it.joinToString() }
        assertFailsWith(IndexOutOfBoundsException::class) { generator[6] }
    }
}
