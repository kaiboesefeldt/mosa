package straightway.mosa.message

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
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
                val page = call.request.queryParameters.page()
                call.respond(
                    HttpStatusCode.OK,
                    adminMessageService.listInboxForRecipient(recipientGid, page),
                    typeInfo<AdminMessageListResult>(),
                )
            }
            getRoute("/admin/messages") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@getRoute
                val recipientGid =
                    call.request.queryParameters["gid"]
                        ?.trim()
                        ?.takeUnless(String::isEmpty)
                        ?: throw BadRequestException("gid is required")
                val page = call.request.queryParameters.page()
                call.respond(
                    HttpStatusCode.OK,
                    adminMessageService.listAdminMessagesForRecipient(
                        requesterGid = requesterGid,
                        recipientGid = recipientGid,
                        page = page,
                    ),
                    typeInfo<AdminMessageListResult>(),
                )
            }
        }
    }
}

private fun io.ktor.http.Parameters.page(): Int =
    this["page"]?.let { value ->
        value.toIntOrNull() ?: throw BadRequestException("page must be an integer")
    } ?: 1
