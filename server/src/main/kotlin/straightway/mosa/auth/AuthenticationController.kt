package straightway.mosa.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import straightway.mosa.base.BaseController
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.config.contains
import straightway.mosa.defense.ratelimit.RateLimiter
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class AuthenticationController(
    private val podCertificateService: PodCertificateService,
    private val clock: Clock,
    private val certificateLifeTime: Duration = 7.days,
    private val approveGidRateLimiter: RateLimiter,
    private val clientIpResolver: ClientIpResolver,
    private val identityBlockList: AutoRefreshingFileBackedContent<IdentityBlockList>,
) : BaseController() {
    private val refreshThresholdSeconds = certificateLifeTime.inWholeSeconds / 10

    override fun Route.register() {
        postRoute("/approvegid") {
            val rateLimitDecision = approveGidRateLimiter.check(call.clientIpAddress())
            if (!rateLimitDecision.allowed) {
                call.response.headers.append(HttpHeaders.RetryAfter, rateLimitDecision.retryAfterSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf("message" to APPROVE_GID_RATE_LIMIT_MESSAGE),
                )
                return@postRoute
            }
            val request = call.receive<AuthenticationApprovalRequest>()
            val blockList = identityBlockList.get()
            if (blockList != null && request.gid in blockList) {
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to IDENTITY_BLOCKED_MESSAGE))
                return@postRoute
            }
            val cert =
                podCertificateService.approveGid(
                    gid = request.gid,
                    validitySeconds = certificateLifeTime.inWholeSeconds,
                )
            call.respond<PodCertificateResponse>(
                HttpStatusCode.Accepted,
                cert.withRefreshRecommendation(),
            )
        }
        authenticate {
            postRoute("/refreshgid") {
                val certificate = userPodCertificateOrRespondUnauthorized() ?: return@postRoute
                val remainingSeconds = certificate.exp - clock.instant().epochSecond
                if (refreshThresholdSeconds <= remainingSeconds) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("message" to REFRESH_TOO_FRESH_MESSAGE),
                    )
                    return@postRoute
                }
                val cert =
                    podCertificateService.approveGid(
                        gid = certificate.gid,
                        validitySeconds = certificateLifeTime.inWholeSeconds,
                        inf = certificate.inf,
                    )
                call.respond<PodCertificateResponse>(
                    HttpStatusCode.Accepted,
                    cert.withRefreshRecommendation(),
                )
            }
        }
    }

    private fun PodCertificate.withRefreshRecommendation(): PodCertificateResponse =
        PodCertificateResponse(
            certificate = this,
            refreshRecommendedAt = refreshRecommendationAt(exp),
        )

    private fun refreshRecommendationAt(expiration: Long): Long = expiration - (refreshThresholdSeconds / 2)

    private fun ApplicationCall.clientIpAddress(): String =
        clientIpResolver.resolve(
            remoteHost = request.local.remoteHost,
            forwardedFor = request.headers[HttpHeaders.XForwardedFor],
        )

    private companion object {
        private const val REFRESH_TOO_FRESH_MESSAGE = "Certificate is too fresh to refresh"
        private const val APPROVE_GID_RATE_LIMIT_MESSAGE = "Rate limit exceeded"
        private const val IDENTITY_BLOCKED_MESSAGE = "Identity blocked"
    }
}
