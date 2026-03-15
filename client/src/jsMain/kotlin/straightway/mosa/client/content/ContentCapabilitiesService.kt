package straightway.mosa.client.content

import straightway.mosa.client.pod.PodSessionProvider
import straightway.mosa.content.ContentCapabilitiesResult

class ContentCapabilitiesService(
    private val podSessionProvider: PodSessionProvider,
) {
    suspend fun fetch(): ContentCapabilitiesResult? =
        podSessionProvider.execute("content capabilities") { session ->
            session.podAccess.contentCapabilities(session.jwt)
        }
}
