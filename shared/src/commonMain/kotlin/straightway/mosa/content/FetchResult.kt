package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class FetchResult(
    val entries: List<Entry>,
) {
    @Serializable
    data class Entry(
        val id: String,
        val content: String,
        val authorGID: String,
        // epoch seconds; aligns with server JSON
        val storedAt: Long,
        val rootId: String? = null,
    )
}
