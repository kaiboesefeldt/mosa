package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.ContentCapabilitiesResult

class ContentCapabilitiesService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(gid: String? = null): ContentCapabilitiesResult? =
        podSessionProvider.execute("content capabilities") { session ->
            session.podAccess.contentCapabilities(session.jwt, gid)
        }

    suspend fun isAdmin(gid: String): Boolean = fetch(gid)?.isAdmin == true
}
