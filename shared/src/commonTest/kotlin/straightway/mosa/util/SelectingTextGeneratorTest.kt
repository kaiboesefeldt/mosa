package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectingTextGeneratorTest {
    @Test
    fun `numberOfPossibleOutputs is sum of options`() {
        val generator = anyOf("1", "2", "3") / anyOf("1", "2")
        assertEquals(2 + 3, generator.size)
    }

    @Test
    fun `iterator yields union of all options`() {
        val generator = anyOf("a", "b") / anyOf("c", "d", "e")
        assertEquals(
            listOf("a", "b", "c", "d", "e"),
            generator.toList()
        )
    }

    @Test
    fun `iterator with single option`() {
        val generator = anyOf("a", "b") / "c"
        assertEquals(
            listOf("a", "b", "c"),
            generator.toList()
        )
    }

    @Test
    fun `get gets item with index`() {
        val generator = anyOf("1", "2") / anyOf("a", "b", "c")
        val generated = (0L..4L).map { generator[it] }
        assertEquals(
            listOf("1", "2", "a", "b", "c"),
            generated
        )
    }

    @Test
    fun `get with exceeding index throws IndexOutOfBoundsException`() {
        val generator = anyOf("1", "2") / anyOf("a", "b", "c")
        assertFailsWith(IndexOutOfBoundsException::class) { generator[5] }
    }
}
