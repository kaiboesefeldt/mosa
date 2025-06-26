package straightway.mosa.content

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

data class QueryOnRequest(val queryOnId: String, val maxResults: Int)

class ContentController(
    private val contentService: ContentService
) : BaseController() {
    override fun Routing.register() {
        postRouteAuthorized("/publish") {
            contentService.postContent(call.receive<PublishRequest>())
            call.respond(HttpStatusCode.Created, null)
        }
        postRouteAuthorized("/reply") {
            contentService.reply(call.receive<ReplyRequest>())
            call.respond(HttpStatusCode.Created, null)
        }
        postRouteAuthorized("/query") {
            val result = contentService.query(call.receive<QueryRequest>())
            call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
        }
        postRouteAuthorized("/queryon") {
            val result = contentService.queryOn(call.receive<QueryOnRequest>())
            call.respond(HttpStatusCode.OK, result, typeInfo<QueryResult>())
        }
    }
}