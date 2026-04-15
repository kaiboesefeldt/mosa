package straightway.mosa.client.identity.privacy

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.identity.privacy.DeleteIdentityResult

class IdentityDeletionService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun deleteCurrentIdentity(): DeleteIdentityResult? =
        podSessionProvider.execute("delete identity") { session ->
            session.podAccess.deleteIdentity(session.jwt)
        }
}
