package straightway.mosa.authorization

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import straightway.mosa.base.BaseController
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class AuthorizationController(
    private val podCertificateService: PodCertificateService,
) : BaseController() {
    override fun Route.register() {
        postRoute("/approvegid") {
            val request = call.receive<AuthorizationRequest>()
            val cert =
                podCertificateService.approveGid(
                    gid = request.gid,
                    validitySeconds = certificateLifeTime.inWholeSeconds,
                )
            call.respond<PodCertificate>(HttpStatusCode.Accepted, cert)
        }
    }

    private companion object {
        private val certificateLifeTime: Duration = 7.days
    }
}
