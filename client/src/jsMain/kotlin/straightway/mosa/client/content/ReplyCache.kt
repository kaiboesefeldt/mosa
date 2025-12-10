package straightway.mosa.client.content

interface ReplyCache {
    suspend fun snapshot(rootId: String): ReplyCacheSnapshot
    suspend fun append(rootId: String, entries: List<ThreadScopedReply>): ReplyCacheSnapshot
    suspend fun resolve(ids: Collection<String>): CachedPostResolution
    suspend fun clear(rootId: String)
}
