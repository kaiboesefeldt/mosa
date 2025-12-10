package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.ReplyRequest

class ReplyPublisher(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun publish(rawText: String, rootId: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val result =
            podSessionProvider.execute("reply") { session ->
                session.podAccess.reply(
                    ReplyRequest(
                        content = PublishRequest(text),
                        rootId = rootId,
                    ),
                    session.jwt,
                )
                true
            }
        return result ?: false
    }
}
