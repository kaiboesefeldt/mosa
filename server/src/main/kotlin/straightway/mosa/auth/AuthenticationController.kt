package straightway.mosa.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import straightway.mosa.base.BaseController
import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class AuthenticationController(
    private val podCertificateService: PodCertificateService,
    private val clock: Clock,
    private val certificateLifeTime: Duration = DEFAULT_CERTIFICATE_LIFETIME,
) : BaseController() {
    private val refreshThresholdSeconds = certificateLifeTime.inWholeSeconds / 10

    override fun Route.register() {
        postRoute("/approvegid") {
            val request = call.receive<AuthenticationApprovalRequest>()
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

    private companion object {
        private val DEFAULT_CERTIFICATE_LIFETIME: Duration = 7.days
        private const val REFRESH_TOO_FRESH_MESSAGE = "Certificate is too fresh to refresh"
    }
}
