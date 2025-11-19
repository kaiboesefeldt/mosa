package straightway.mosa.client.ui

import straightway.mosa.content.QueryResult

internal fun filterEntriesById(
    entries: List<QueryResult.Entry>,
    filterEntryId: String?,
): List<QueryResult.Entry> {
    if (filterEntryId.isNullOrEmpty()) return entries
    val index = entries.indexOfFirst { it.id == filterEntryId }
    if (index < 0) return entries
    return entries.take(index + 1)
}
