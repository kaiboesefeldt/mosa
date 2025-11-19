package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import straightway.mosa.content.QueryResult

class TopicPostFilterTest {
    @Test
    fun `returns all entries when filter is missing`() {
        val entries = listOf(entry("one", 0.9f), entry("two", 0.8f))

        val result = filterEntriesByScore(entries, null)

        assertEquals(entries, result.entries)
        assertEquals(null, result.filteredEntryId)
    }

    @Test
    fun `keeps entries with higher score and the boundary entry`() {
        val entries = listOf(entry("one", 0.95f), entry("two", 0.9f), entry("three", 0.7f))

        val result = filterEntriesByScore(entries, 0.9f)

        assertEquals(listOf(entries[0], entries[1]), result.entries)
        assertEquals("two", result.filteredEntryId)
    }

    @Test
    fun `shows only the highest filtered entry when everything is below the score`() {
        val entries = listOf(entry("one", 0.9f), entry("two", 0.85f), entry("three", 0.8f))

        val result = filterEntriesByScore(entries, 0.95f)

        assertEquals(listOf(entries[0]), result.entries)
        assertEquals("one", result.filteredEntryId)
    }

    private fun entry(id: String, similarity: Float) =
        QueryResult.Entry(
            id = id,
            content = "",
            similarity = similarity,
            authorGID = "",
            storedAt = 0,
        )
}
