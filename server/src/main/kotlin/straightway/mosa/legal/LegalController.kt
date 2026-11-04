package straightway.mosa.legal

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.serialization.json.Json
import straightway.mosa.base.BaseController

class LegalController(
    private val legalService: LegalService,
    private val imprintService: ImprintService,
    private val startupStatus: LegalStartupStatus,
) : BaseController() {
    override fun Route.register() {
        getRoute("/legal/status") {
            call.respondText(
                text = Json.encodeToString(LegalStatus(startupStatus.complete)),
                contentType = ContentType.Application.Json,
            )
        }
        getRoute("/legal/privacy") {
            call.respondText(
                text = Json.encodeToString(legalService.privacyDocumentChainResponse()),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
        getRoute("/legal/terms") {
            call.respondText(
                text = Json.encodeToString(legalService.termsDocumentChainResponse()),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
        getRoute("/legal/imprint") {
            call.respondText(
                text = Json.encodeToString(imprintService.document()),
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK,
            )
        }
    }
}
