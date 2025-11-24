package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

interface PostCache {
    suspend fun snapshot(topic: String): PostCacheSnapshot
    suspend fun append(topic: String, entries: List<QueryResult.Entry>): PostCacheSnapshot
    suspend fun clear(topic: String)
}