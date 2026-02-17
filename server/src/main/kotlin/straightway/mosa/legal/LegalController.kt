package straightway.mosa.legal

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import straightway.mosa.base.BaseController

class LegalController(
    private val legalService: LegalService,
) : BaseController() {
    override fun Route.register() {
        getRoute("/legal/privacy/baseline") {
            call.respondText(
                text = legalService.privacyBaselineCanonicalJson(),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
        getRoute("/legal/privacy/default") {
            call.respondText(
                text = legalService.privacyDefaultCanonicalJson(),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
        getRoute("/legal/privacy/mosa") {
            call.respondText(
                text = legalService.privacyMosaCanonicalJson(),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
        getRoute("/legal/privacy/operator") {
            call.respondText(
                text = legalService.privacyServiceCanonicalJson(),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
    }
}
