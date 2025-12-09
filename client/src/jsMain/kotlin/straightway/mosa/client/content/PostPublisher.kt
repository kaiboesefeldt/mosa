package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.PublishRequest

class PostPublisher(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun publish(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val result =
            podSessionProvider.execute("publish") { session ->
                session.podAccess.publish(PublishRequest(text), session.jwt)
                true
            }
        return result ?: false
    }
}
