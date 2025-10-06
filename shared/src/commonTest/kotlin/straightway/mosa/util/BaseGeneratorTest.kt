package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BaseGeneratorTest {

    class DerivedTextGenerator(
        override val size: Long = items.size.toLong(),
        val items: List<String> = emptyList(),
    ) : BaseGenerator<String>() {
        override operator fun get(index: Long): String = items[index.toInt()]
    }

    @Test
    fun `iterator with negative size yields nothing`() {
        val sut = DerivedTextGenerator(size = -1)
        assertEquals(emptyList(), sut.toList())
    }

    @Test
    fun `iterator with zero size yields nothing`() {
        val sut = DerivedTextGenerator(size = 0)
        assertEquals(emptyList(), sut.toList())
    }

    @Test
    fun `iterator yields as many items as size specifies`() {
        val sut = DerivedTextGenerator(size = 2, items = listOf("1", "2", "3"))
        assertEquals(listOf("1", "2"), sut.toList())
    }
}