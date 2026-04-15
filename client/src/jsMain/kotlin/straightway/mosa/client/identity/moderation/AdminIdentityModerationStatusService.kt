package straightway.mosa.client.identity.moderation

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.identity.moderation.AdminIdentityModerationStatus

class AdminIdentityModerationStatusService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(gid: String): AdminIdentityModerationStatus? {
        val trimmedGid = gid.trim()
        if (trimmedGid.isEmpty()) return null
        return podSessionProvider.execute("admin identity moderation status") { session ->
            session.podAccess.adminIdentityModerationStatus(trimmedGid, session.jwt)
        }
    }
}
