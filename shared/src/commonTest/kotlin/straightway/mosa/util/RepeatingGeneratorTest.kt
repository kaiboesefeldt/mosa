package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RepeatingGeneratorTest {
    @Test
    fun `size is toRepeat size times repetitions`() {
        val generator = (1..3) * anyOf("1", "2", "3", "4")
        assertEquals(4 + 4 * 4 + 4 * 4 * 4, generator.size)
    }

    @Test
    fun `iterator yields repeated combinations`() {
        val generator = stringGen((2..3) * anyOf("a", "b"))
        assertEquals(
            listOf("aa", "ba", "ab", "bb", "aaa", "baa", "aba", "bba", "aab", "bab", "abb", "bbb"),
            generator.toList(),
        )
    }

    @Test
    fun `get applies custom joiner`() {
        val generator = (2 * anyOf("first", "second", "third")).joinBy { it.joinToString(" | ") }
        val result = generator[1]
        assertEquals("second | first", result)
    }

    @Test
    fun `generate with zero repetitions has no values`() {
        val generator = 0 * anyOf("a", "b")

        assertEquals(0, generator.size)
        assertEquals(emptyList(), generator.toList())
        assertFailsWith(IndexOutOfBoundsException::class) { generator[0] }
    }

    @Test
    fun `generate with empty repetition range has no values`() {
        val generator = (1..0) * anyOf("a", "b")

        assertEquals(0, generator.size)
        assertEquals(emptyList(), generator.toList())
        assertFailsWith(IndexOutOfBoundsException::class) { generator[0] }
    }

    @Test
    fun `get gets item with index`() {
        val generator = stringGen((1..3) * anyOf("1", "2"))
        val generated = (0L..13L).map { generator[it] }
        assertEquals(
            listOf(
                "1",
                "2",
                "11",
                "21",
                "12",
                "22",
                "111",
                "211",
                "121",
                "221",
                "112",
                "212",
                "122",
                "222",
            ),
            generated,
        )
    }

    @Test
    fun `get with exceeding index throws IndexOutOfBoundsException`() {
        val generator = (1..3) * anyOf("1", "2")
        assertFailsWith(IndexOutOfBoundsException::class) { generator[14] }
    }
}
