package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.message.AdminMessageListResult

class AdminMessageHistoryService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(
        recipientGid: String,
        page: Int,
    ): AdminMessageListResult? {
        val trimmedRecipientGid = recipientGid.trim()
        if (trimmedRecipientGid.isEmpty() || page < 1) {
            return null
        }
        return podSessionProvider.execute("admin messages") { session ->
            session.podAccess.adminMessages(
                recipientGid = trimmedRecipientGid,
                jwt = session.jwt,
                page = page,
            )
        }
    }
}
