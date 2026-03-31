package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.DeleteIdentityResult

class IdentityDeletionService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun deleteCurrentIdentity(): DeleteIdentityResult? =
        podSessionProvider.execute("delete identity") { session ->
            session.podAccess.deleteIdentity(session.jwt)
        }
}
