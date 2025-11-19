package straightway.mosa.client.ui

import straightway.mosa.content.QueryResult

internal data class TopicFilterResult(
    val entries: List<QueryResult.Entry>,
    val filteredEntryId: String?,
)

internal fun filterEntriesByScore(
    entries: List<QueryResult.Entry>,
    filterScore: Float?,
): TopicFilterResult {
    if (filterScore == null) return TopicFilterResult(entries, null)
    val index = entries.indexOfFirst { it.similarity <= filterScore }
    if (index < 0) return TopicFilterResult(entries, null)
    val boundaryEntry = entries[index]
    val visibleEntries =
        if (index == 0) {
            listOf(boundaryEntry)
        } else {
            entries.take(index) + boundaryEntry
        }
    return TopicFilterResult(visibleEntries, boundaryEntry.id)
}
