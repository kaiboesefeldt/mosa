package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider

class PostDeletionService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun delete(postId: String): Boolean {
        if (postId.isBlank()) return false
        val result =
            podSessionProvider.execute("delete post") { session ->
                session.podAccess.deletePost(postId, session.jwt)
                true
            }
        return result ?: false
    }
}
