package straightway.mosa.client.content

data class ReplyCacheSnapshot(
    val entries: List<ThreadScopedReply>,
    val lastFetchedAt: Long?,
)
