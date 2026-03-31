package straightway.mosa.defense.blocklist

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.config.contains

fun createMosaAuthenticatedIdentityBlockListPlugin(identityBlockList: AutoRefreshingFileBackedContent<IdentityBlockList>) =
    createRouteScopedPlugin("MosaIdentityBlockList") {
        on(AuthenticationChecked) { call ->
            if (call.request.httpMethod == HttpMethod.Delete && call.request.path() == IDENTITY_DELETE_PATH) {
                return@on
            }
            val principal = call.principal<PodPrincipal>() ?: return@on
            val blockList = identityBlockList.get() ?: return@on
            if (principal.gid !in blockList) return@on
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to IDENTITY_BLOCKED_MESSAGE))
        }
    }

private const val IDENTITY_BLOCKED_MESSAGE = "Identity blocked"
private const val IDENTITY_DELETE_PATH = "/v1/identity"
