package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class IdentityModerationController(
    private val identityModerationService: IdentityModerationService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/admin/identities/block") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@postRoute
                identityModerationService.block(requesterGid, call.receive<IdentityModerationRequest>().gid)
                call.respond(HttpStatusCode.NoContent, null)
            }
            postRoute("/admin/identities/unblock") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@postRoute
                identityModerationService.unblock(requesterGid, call.receive<IdentityModerationRequest>().gid)
                call.respond(HttpStatusCode.NoContent, null)
            }
            deleteRoute("/admin/identities/{gid}") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                val targetGid = call.parameters["gid"] ?: error("Missing gid route parameter")
                val result = identityModerationService.delete(requesterGid, targetGid)
                call.respond(HttpStatusCode.OK, result, typeInfo<DeleteIdentityResult>())
            }
        }
    }
}
