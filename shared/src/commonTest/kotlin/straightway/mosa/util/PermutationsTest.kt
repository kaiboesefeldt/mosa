package straightway.mosa.util

import kotlin.test.Test
import kotlin.test.assertEquals

class PermutationsTest {
    @Test
    fun `permutations of empty list is empty`() {
        assertEquals(emptySet(), allPermsOf(emptyList<Int>()).toSet())
    }

    @Test
    fun `permutations of single element list only consist of this list`() {
        assertEquals(setOf(listOf(1)), allPermsOf(listOf(1)).toSet())
    }

    @Test
    fun `permutations of two element list`() {
        assertEquals(
            setOf(listOf(1, 2), listOf(2, 1)),
            allPermsOf(listOf(1, 2)).toSet()
        )
    }

    @Test
    fun `permutations of three element list`() {
        assertEquals(
            setOf(
                listOf(1, 2, 3), listOf(1, 3, 2), listOf(3, 1, 2),
                listOf(2, 1, 3), listOf(2, 3, 1), listOf(3, 2, 1),
            ),
            allPermsOf(listOf(1, 2, 3)).toSet()
        )
    }

    @Test
    fun `permutations of four element list`() {
        assertEquals(
            setOf(
                listOf(1, 2, 3, 4), listOf(1, 2, 4, 3), listOf(1, 4, 2, 3), listOf(4, 1, 2, 3),
                listOf(1, 3, 2, 4), listOf(1, 3, 4, 2), listOf(1, 4, 3, 2), listOf(4, 1, 3, 2),
                listOf(3, 1, 2, 4), listOf(3, 1, 4, 2), listOf(3, 4, 1, 2), listOf(4, 3, 1, 2),
                listOf(2, 1, 3, 4), listOf(2, 1, 4, 3), listOf(2, 4, 1, 3), listOf(4, 2, 1, 3),
                listOf(2, 3, 1, 4), listOf(2, 3, 4, 1), listOf(2, 4, 3, 1), listOf(4, 2, 3, 1),
                listOf(3, 2, 1, 4), listOf(3, 2, 4, 1), listOf(3, 4, 2, 1), listOf(4, 3, 2, 1),
            ),
            allPermsOf(listOf(1, 2, 3, 4)).toSet()
        )
    }
}