package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.message.CreateAdminMessageRequest

class AdminMessagePublisher(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun send(
        recipientGid: String,
        subject: String,
        body: String,
    ): Boolean {
        val trimmedRecipientGid = recipientGid.trim()
        if (trimmedRecipientGid.isEmpty() || subject.isBlank() || body.isBlank()) {
            return false
        }
        return podSessionProvider.execute("admin message") { session ->
            session.podAccess.sendAdminMessage(
                CreateAdminMessageRequest(
                    recipientGid = trimmedRecipientGid,
                    subject = subject,
                    body = body,
                ),
                session.jwt,
            )
            true
        } ?: false
    }
}
