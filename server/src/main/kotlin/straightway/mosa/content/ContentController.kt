package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController
import java.time.Clock

class ContentController(
    private val contentService: ContentService,
    private val clock: Clock,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/publish") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                contentService.publish(
                    gid,
                    call.receive<PublishRequest>(),
                    clock.instant().epochSecond,
                )
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/reply") {
                val gid = userGidOrRespondUnauthorized() ?: return@postRoute
                contentService.reply(
                    gid,
                    call.receive<ReplyRequest>(),
                    clock.instant().epochSecond,
                )
                call.respond(HttpStatusCode.Created, null)
            }
            deleteRoute("/posts") {
                val gid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                contentService.deleteAllForUser(gid)
                call.respond(HttpStatusCode.NoContent, null)
            }
            deleteRoute("/posts/{id}") {
                val gid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                val id = call.parameters["id"] ?: throw BadRequestException("id is required")
                contentService.delete(gid, id)
                call.respond(HttpStatusCode.NoContent, null)
            }
            postRoute("/query") {
                userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.query(call.receive<QueryRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
            postRoute("/queryon") {
                userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.queryOn(call.receive<QueryOnRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
            postRoute("/search") {
                userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.search(call.receive<QueryRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<SearchResult>())
            }
            postRoute("/searchon") {
                userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.searchOn(call.receive<QueryOnRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<SearchResult>())
            }
            postRoute("/fetch") {
                userGidOrRespondUnauthorized() ?: return@postRoute
                val result = contentService.fetch(call.receive<FetchRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<FetchResult>())
            }
        }
    }
}
