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
                true
            }
        if (result == true) {
            postCache.evict(postId)
            replyCache.evict(postId)
            return true
        }
        return false
    }
}
