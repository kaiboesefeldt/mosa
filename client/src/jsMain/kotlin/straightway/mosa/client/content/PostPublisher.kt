package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
class PostPublisher(
    private val podSessionProvider: PodSessionProvider,
    private val contentSigner: ContentSigner,
) {
    suspend fun publish(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val result =
            podSessionProvider.execute("publish") { session ->
                session.podAccess.publish(contentSigner.signPost(session.identity, text), session.jwt)
                true
            }
        return result ?: false
    }
}
