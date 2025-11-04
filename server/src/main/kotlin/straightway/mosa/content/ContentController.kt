package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class ContentController(
    private val contentService: ContentService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/publish") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                contentService.publish(gid, call.receive<PublishRequest>())
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/reply") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                contentService.reply(gid, call.receive<ReplyRequest>())
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/query") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.query(gid, call.receive<QueryRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
            postRoute("/queryon") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.queryOn(gid, call.receive<QueryOnRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
        }
    }

    private suspend fun RoutingContext.userGidOrRespondUnauthorized(): String? {
        val principal =
            runCatching {
                requireNotNull(call.principal<UserIdPrincipal>()) {
                    "Missing authenticated principal for ${call.request.path()}"
                }
            }.getOrElse {
                val description = it.message ?: "Missing authenticated principal"
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to description))
                return null
            }
        return principal.name
    }
}
