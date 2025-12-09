package straightway.mosa.client.content

interface PostCache {
    suspend fun snapshot(topic: String): PostCacheSnapshot
    suspend fun append(topic: String, entries: List<TopicScopedPost>): PostCacheSnapshot
    suspend fun resolve(ids: Collection<String>): CachedPostResolution
    suspend fun clear(topic: String)
}
