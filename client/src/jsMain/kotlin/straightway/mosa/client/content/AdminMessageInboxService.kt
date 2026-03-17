package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.message.AdminMessage

class AdminMessageInboxService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(): List<AdminMessage> =
        podSessionProvider.execute("messages") { session ->
            session.podAccess.messages(session.jwt).entries
        } ?: emptyList()
}
