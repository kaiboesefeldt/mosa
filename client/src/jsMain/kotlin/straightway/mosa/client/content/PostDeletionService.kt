package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider

class PostDeletionService(
    private val podSessionProvider: PodSessionProvider,
    private val postCache: PostCache,
    private val replyCache: ReplyCache,
) {
    suspend fun delete(postId: String): Boolean {
        if (postId.isBlank()) return false
        val result =
            podSessionProvider.execute("delete post") { session ->
                session.podAccess.deletePost(postId, session.jwt)
            }
        if (result != null) {
            result.ids.forEach {
                postCache.evict(it)
                replyCache.evict(it)
            }
            return true
        }
        return false
    }
}
