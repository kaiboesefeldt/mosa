package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class ContentController(
    private val contentService: ContentService,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/publish") {
                contentService.publish(call.receive<PublishRequest>())
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/reply") {
                contentService.reply(call.receive<ReplyRequest>())
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/query") {
                val result = contentService.query(call.receive<QueryRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
            postRoute("/queryon") {
                val result = contentService.queryOn(call.receive<QueryOnRequest>())
                call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
            }
        }
    }
}
