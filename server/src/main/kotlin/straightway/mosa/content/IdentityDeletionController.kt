package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class IdentityDeletionController(
    private val identityDeletionService: IdentityDeletionService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            deleteRoute("/identity") {
                val gid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                val result = identityDeletionService.deleteIdentity(gid)
                call.respond(HttpStatusCode.OK, result, typeInfo<DeleteIdentityResult>())
            }
        }
    }
}
