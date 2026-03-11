package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.ReplyRequest

class ReplyPublisher(
    private val podSessionProvider: PodSessionProvider,
    private val contentSigner: ContentSigner,
) {
    suspend fun publish(rawText: String, rootId: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val result =
            podSessionProvider.execute("reply") { session ->
                session.podAccess.reply(
                    ReplyRequest(
                        content = contentSigner.signReply(session.identity, text, rootId),
                        rootId = rootId,
                    ),
                    session.jwt,
                )
                true
            }
        return result ?: false
    }
}
