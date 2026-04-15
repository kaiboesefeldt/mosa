package straightway.mosa.identity.moderation

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController
import straightway.mosa.identity.privacy.DeleteIdentityResult

class IdentityModerationController(
    private val identityModerationService: IdentityModerationService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            getRoute("/admin/identities/capabilities") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@getRoute
                val targetGid = call.request.queryParameters["gid"] ?: error("Missing gid query parameter")
                val result = identityModerationService.status(requesterGid, targetGid)
                call.respond(HttpStatusCode.OK, result, typeInfo<AdminIdentityModerationStatus>())
            }
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
