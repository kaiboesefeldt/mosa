package straightway.mosa.client.pod

import io.ktor.http.HttpStatusCode
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger

class PodSessionProvider(
    private val identityRepository: IdentitySelectionRepository,
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val jwtBuilder: JwtBuilder,
    private val certificateCache: PodCertificateCache,
    private val logger: Logger,
) {
    suspend fun <T> execute(
        requestName: String,
        call: suspend (PodSession) -> T,
    ): T? {
        val podBase = podBaseProvider.base() ?: return null
        val identity = loadIdentity() ?: return null
        val podAccess = podAccess(podBase) ?: return null
        val session = podSession(podBase, identity, podAccess) ?: return null
        val firstAttempt = runCatching { call(session) }
        val failure = firstAttempt.exceptionOrNull()
        if (failure == null) return firstAttempt.getOrNull()

        if (failure !is PodRequestFailed || !failure.isUnauthorized()) {
            logger.warn("$requestName failed", failure)
            return null
        }

        certificateCache.invalidate(podBase, identity.gid)
        val refreshedSession = podSession(podBase, identity, podAccess) ?: return null
        return runCatching { call(refreshedSession) }
            .onFailure { logger.warn("$requestName failed after refresh", it) }
            .getOrNull()
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

    private suspend fun podSession(
        podBase: String,
        identity: Identity,
        podAccess: PodAccess,
    ): PodSession? {
        val certificate = certificate(podBase, identity, podAccess) ?: return null
        val jwt = jwt(identity, certificate) ?: return null
        return PodSession(podAccess, jwt)
    }

    private suspend fun certificate(
        podBase: String,
        identity: Identity,
        podAccess: PodAccess,
    ): PodCertificate? {
        val cached = certificateCache.cached(podBase, identity.gid)
        if (cached != null) return cached
        return runCatching { podAccess.approveGid(identity.gid) }
            .onSuccess { certificateCache.store(podBase, identity.gid, it) }
            .getOrElse {
                logger.error("Pod certificate retrieval failed", it)
                null
            }
    }

    private suspend fun jwt(identity: Identity, certificate: PodCertificate): String? =
        runCatching { jwtBuilder(identity, certificate) }
            .getOrElse {
                logger.error("JWT creation failed", it)
                null
            }

    private fun PodRequestFailed.isUnauthorized(): Boolean = status == POD_UNAUTHORIZED

    companion object {
        val POD_UNAUTHORIZED = HttpStatusCode.Unauthorized
    }
}

data class PodSession(
    val podAccess: PodAccess,
    val jwt: String,
)
