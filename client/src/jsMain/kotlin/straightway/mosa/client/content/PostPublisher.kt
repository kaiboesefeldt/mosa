package straightway.mosa.client.content

import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.PodBaseProvider
import straightway.mosa.content.PublishRequest

class PostPublisher(
    private val identityRepository: IdentitySelectionRepository,
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val jwtBuilder: JwtBuilder,
    private val logger: Logger,
) {
    suspend fun publish(rawText: String): Boolean {
        val text = rawText.trim()
        if (text.isEmpty()) return false
        val podBase = podBaseProvider.base() ?: return false
        val identity = loadIdentity() ?: return false
        val podAccess = podAccess(podBase) ?: return false
        val certificate = certificate(podAccess, identity) ?: return false
        val jwt = jwt(certificate, identity) ?: return false
        return runCatching { podAccess.publish(PublishRequest(text), jwt) }
            .onFailure { logger.error("Publish failed", it) }
            .isSuccess
    }

    private suspend fun loadIdentity(): Identity? =
        runCatching { identityRepository.loadOrCreate().identity }
            .getOrElse {
                logger.error("Failed to load identity", it)
                null
            }

    private fun podAccess(podBase: String): PodAccess? =
        runCatching { podAccessFactory(podBase) }
            .getOrElse {
                logger.error("Pod access unavailable", it)
                null
            }

    private suspend fun certificate(podAccess: PodAccess, identity: Identity): PodCertificate? =
        runCatching { podAccess.approveGid(identity.gid) }
            .getOrElse {
                logger.error("Pod certificate retrieval failed", it)
                null
            }

    private suspend fun jwt(certificate: PodCertificate, identity: Identity): String? =
        runCatching { jwtBuilder(identity, certificate) }
            .getOrElse {
                logger.error("JWT creation failed", it)
                null
            }
}
