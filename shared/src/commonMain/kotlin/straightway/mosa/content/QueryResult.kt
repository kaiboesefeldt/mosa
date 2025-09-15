package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class QueryResult(
    val entries: List<Entry>,
    val queryOnId: String,
) {
    @Serializable
    data class Entry(
        val id: String,
        val content: String,
        val similarity: Float,
        val authorGID: String,
        // epoch seconds; aligns with server JSON
        val storedAt: Long,
        val rootId: String? = null,
    )
}
