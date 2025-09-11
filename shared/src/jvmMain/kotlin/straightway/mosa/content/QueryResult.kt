package straightway.mosa.content

import java.time.Instant

data class QueryResult(
    val entries: List<Entry>,
    val queryOnId: String,
) {
    data class Entry(
        val id: String,
        val content: String,
        val similarity: Float,
        val authorGID: String,
        val storedAt: Instant,
        val rootId: String? = null,
    )
}

