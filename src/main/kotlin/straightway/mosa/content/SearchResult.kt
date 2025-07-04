package straightway.mosa.content

data class SearchResult(
    val entries: List<Entry>,
    val searchOnId: String,
) {
    data class Entry(
        val id: String,
        val content: String,
        val similarity: Float,
        val authorGID: String,
        val storedAt: Long,
        val parent: String? = null,
    )
}
