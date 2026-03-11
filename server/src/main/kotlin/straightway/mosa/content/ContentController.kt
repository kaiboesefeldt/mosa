package straightway.mosa.content

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ContentController(
    private val contentService: ContentService,
    private val contentSignatureVerifier: ContentSignatureVerifier,
    private val clock: Clock,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            postRoute("/publish") {
                val principal = userPrincipalOrRespondUnauthorized() ?: return@postRoute
                val request = call.receive<PublishRequest>()
                contentSignatureVerifier.verifyPublish(principal, request)
                contentService.publish(
                    principal.gid,
                    request,
                    clock.instant().epochSecond,
                )
                call.respond(HttpStatusCode.Created, null)
            }
            postRoute("/reply") {
                val principal = userPrincipalOrRespondUnauthorized() ?: return@postRoute
                val request = call.receive<ReplyRequest>()
                contentSignatureVerifier.verifyReply(principal, request)
                contentService.reply(
                    principal.gid,
                    request,
                    clock.instant().epochSecond,
                )
                call.respond(HttpStatusCode.Created, null)
            }
            deleteRoute("/posts") {
                val gid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                val deletedIds = contentService.deleteAllForUser(gid)
                call.respond(HttpStatusCode.OK, DeletePostsResult(deletedIds), typeInfo<DeletePostsResult>())
            }
            deleteRoute("/posts/{id}") {
                val gid = userGidOrRespondUnauthorized() ?: return@deleteRoute
                val id = call.parameters["id"] ?: throw BadRequestException("id is required")
                val deletedIds = contentService.delete(gid, id)
                call.respond(HttpStatusCode.OK, DeletePostsResult(deletedIds), typeInfo<DeletePostsResult>())
            }
            getRoute("/posts/export.csv") {
                val gid = userGidOrRespondUnauthorized() ?: return@getRoute
                val csv = contentService.exportCsvForUser(gid)
                val fileName = "mosa-export-${DateTimeFormatter.ISO_LOCAL_DATE.format(clock.instant().atZone(ZoneOffset.UTC))}.csv"
                call.response.header(
                    "Content-Disposition",
                    ContentDisposition.Attachment
                        .withParameter(ContentDisposition.Parameters.FileName, fileName)
                        .toString(),
                )
                call.respondText(csv, ContentType.parse("text/csv; charset=utf-8"), HttpStatusCode.OK)
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
