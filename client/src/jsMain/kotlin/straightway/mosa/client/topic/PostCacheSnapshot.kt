package straightway.mosa.client.topic

data class PostCacheSnapshot(
    val entries: List<TopicScopedPost>,
    val lastFetchedAt: Long?,
)