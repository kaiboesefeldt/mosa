package straightway.mosa.content

data class SearchResult(
    val entries: List<Entry>,
    val queryOnId: String,
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
