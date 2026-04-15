package straightway.mosa.client.identity.moderation

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.identity.moderation.IdentityModerationRequest

class IdentityModerationService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun block(gid: String): Boolean = moderate(gid) { session, request ->
        session.podAccess.blockIdentity(request, session.jwt)
    }

    suspend fun unblock(gid: String): Boolean = moderate(gid) { session, request ->
        session.podAccess.unblockIdentity(request, session.jwt)
    }

    suspend fun delete(gid: String): Boolean {
        val trimmedGid = gid.trim()
        if (trimmedGid.isEmpty()) return false
        return podSessionProvider.execute("delete identity as admin") { session ->
            session.podAccess.deleteIdentityAsAdmin(trimmedGid, session.jwt)
            true
        } ?: false
    }

    private suspend fun moderate(
        gid: String,
        call: suspend (straightway.mosa.client.pod.PodSession, IdentityModerationRequest) -> Unit,
    ): Boolean {
        val trimmedGid = gid.trim()
        if (trimmedGid.isEmpty()) return false
        return podSessionProvider.execute("identity moderation") { session ->
            call(session, IdentityModerationRequest(trimmedGid))
            true
        } ?: false
    }
}
