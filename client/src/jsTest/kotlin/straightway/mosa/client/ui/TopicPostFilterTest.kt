package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.content.QueryResult

class TopicPostFilterTest {
    @Test
    fun `returns all entries when filter is missing`() {
        val entries = listOf(entry("one"), entry("two"), entry("three"))

        val result = filterEntriesById(entries, null)

        assertEquals(entries, result)
    }

    @Test
    fun `returns entries up to selected id`() {
        val entries = listOf(entry("one"), entry("two"), entry("three"))

        val result = filterEntriesById(entries, "two")

        assertEquals(listOf(entries[0], entries[1]), result)
    }

    @Test
    fun `keeps entries when id is not found`() {
        val entries = listOf(entry("one"), entry("two"))

        val result = filterEntriesById(entries, "missing")

        assertEquals(entries, result)
    }

    private fun entry(id: String) =
        QueryResult.Entry(
            id = id,
            content = "",
            similarity = 0f,
            authorGID = "",
            storedAt = 0,
        )
}
