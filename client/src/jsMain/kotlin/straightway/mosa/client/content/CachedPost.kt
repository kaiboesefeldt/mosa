package straightway.mosa.client.content

data class CachedPost(
    val id: String,
    val content: String,
    val authorGID: String,
    val storedAt: Long,
    val rootId: String? = null,
)