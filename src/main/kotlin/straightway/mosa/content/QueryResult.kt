package straightway.mosa.content

data class QueryResult(val entries: List<Entry>, val queryOnId: String) {
    data class Entry constructor(
        val id: String,
        val content: String,
        val similarity: Float,
        val authorGID: String,
        val storedAt: Long,
        val parent: String? = null,
    )
}