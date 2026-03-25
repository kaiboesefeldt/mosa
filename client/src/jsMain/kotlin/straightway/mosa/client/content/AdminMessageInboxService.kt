package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.message.AdminMessage
import straightway.mosa.message.AdminMessageListResult

class AdminMessageInboxService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(
        page: Int,
        fromTime: Long = 0,
    ): AdminMessageListResult? {
        if (page < 1) {
            return null
        }
        return podSessionProvider.execute("messages") { session ->
            session.podAccess.messages(session.jwt, page, fromTime)
        }
    }

    suspend fun fetchEntries(
        page: Int,
        fromTime: Long = 0,
    ): List<AdminMessage> =
        podSessionProvider.execute("messages") { session ->
            session.podAccess.messages(session.jwt, page, fromTime).entries
        } ?: emptyList()
}
