package straightway.mosa.message

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class AdminMessageController(
    private val adminMessageService: AdminMessageService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/admin/messages") {
                val senderGid = userGidOrRespondUnauthorized() ?: return@postRoute
                adminMessageService.send(senderGid, call.receive<CreateAdminMessageRequest>())
                call.respond(HttpStatusCode.NoContent, null)
            }
            getRoute("/messages") {
                val recipientGid = userGidOrRespondUnauthorized() ?: return@getRoute
                call.respond(
                    HttpStatusCode.OK,
                    adminMessageService.listForRecipient(recipientGid),
                    typeInfo<AdminMessageListResult>(),
                )
            }
        }
    }
}
