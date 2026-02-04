package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider

class AllPostsDeletionService(
    private val podSessionProvider: PodSessionProvider,
    private val postCache: PostCache,
    private val replyCache: ReplyCache,
) {
    suspend fun deleteAll(): List<String>? {
        val result =
            podSessionProvider.execute("delete all posts") { session ->
                session.podAccess.deletePosts(session.jwt)
            }
        val ids = result?.ids ?: return null
        ids.forEach {
            postCache.evict(it)
            replyCache.evict(it)
        }
        return ids
    }
}
