package straightway.mosa.client.pod

import io.ktor.http.HttpStatusCode
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.content.JwtBuilder
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.logging.Logger
import kotlin.time.Duration.Companion.hours

class PodSessionProvider(
    private val identityRepository: IdentitySelectionRepository,
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val jwtBuilder: JwtBuilder,
    private val certificateCache: PodCertificateCache,
    private val timeService: TimeService,
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
        val response = certificate(podBase, identity, podAccess) ?: return null
        val jwt = jwt(identity, response.certificate) ?: return null
        return PodSession(podAccess, jwt)
    }

    private suspend fun certificate(
        podBase: String,
        identity: Identity,
        podAccess: PodAccess,
    ): PodCertificateResponse? {
        val cached = certificateCache.cached(podBase, identity.gid)
        if (cached != null) {
            val refreshed = refreshIfNeeded(podBase, identity, podAccess, cached) ?: return null
            if (refreshed != cached) {
                certificateCache.store(podBase, identity.gid, refreshed)
            }
            return refreshed
        }
        return approveNewCertificate(podBase, identity, podAccess)
    }

    private suspend fun jwt(identity: Identity, certificate: PodCertificate): String? =
        runCatching { jwtBuilder(identity, certificate) }
            .getOrElse {
                logger.error("JWT creation failed", it)
                null
            }

    private suspend fun refreshIfNeeded(
        podBase: String,
        identity: Identity,
        podAccess: PodAccess,
        cached: PodCertificateResponse,
    ): PodCertificateResponse? {
        if (!shouldRefresh(cached)) return cached
        val refreshJwt = jwt(identity, cached.certificate) ?: return cached
        val refreshedAttempt = runCatching { podAccess.refreshGid(refreshJwt) }
        val refreshed = refreshedAttempt.getOrNull()
        if (refreshed != null) return refreshed

        val failure = refreshedAttempt.exceptionOrNull()
        if (failure is PodRequestFailed) {
            if (failure.status == POD_BAD_REQUEST) return cached
            if (failure.status == POD_UNAUTHORIZED) return approveNewCertificate(podBase, identity, podAccess)
        }
        logger.warn("Pod certificate refresh failed", failure)
        return cached
    }

    private suspend fun approveNewCertificate(
        podBase: String,
        identity: Identity,
        podAccess: PodAccess,
    ): PodCertificateResponse? =
        runCatching { podAccess.approveGid(identity.gid) }
            .onSuccess { certificateCache.store(podBase, identity.gid, it) }
            .getOrElse {
                logger.error("Pod certificate retrieval failed", it)
                null
            }

    private fun shouldRefresh(certificate: PodCertificateResponse): Boolean {
        val recommendedAt = certificate.refreshRecommendedAt
        if (recommendedAt > 0) return timeService.now >= recommendedAt
        val remainingSeconds = certificate.certificate.exp - timeService.now
        return remainingSeconds < refreshThresholdSeconds
    }

    private fun PodRequestFailed.isUnauthorized(): Boolean = status == POD_UNAUTHORIZED

    companion object {
        val POD_UNAUTHORIZED = HttpStatusCode.Unauthorized
        private val POD_BAD_REQUEST = HttpStatusCode.BadRequest
        private val refreshThresholdSeconds = 2.hours.inWholeSeconds
    }
}

data class PodSession(
    val podAccess: PodAccess,
    val jwt: String,
)
