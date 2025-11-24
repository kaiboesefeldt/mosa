package straightway.mosa.client.content

data class PostCacheSnapshot(
    val entries: List<TopicScopedPost>,
    val lastFetchedAt: Long?,
)