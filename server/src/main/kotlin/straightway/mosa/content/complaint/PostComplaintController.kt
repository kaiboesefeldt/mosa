package straightway.mosa.content.complaint

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.util.reflect.typeInfo
import straightway.mosa.base.BaseController

class PostComplaintController(
    private val postComplaintService: PostComplaintService,
    private val postComplaintSignatureVerifier: PostComplaintSignatureVerifier,
) : BaseController() {
    override fun Route.register() {
        authenticate {
            getRoute("/posts/{id}/complaint-status") {
                userGidOrRespondUnauthorized() ?: return@getRoute
                val postId = call.parameters["id"] ?: throw BadRequestException("id is required")
                call.respond(
                    HttpStatusCode.OK,
                    postComplaintService.status(postId),
                    typeInfo<PostComplaintAvailabilityResult>(),
                )
            }
            postRoute("/post-complaints") {
                val principal = userPrincipalOrRespondUnauthorized() ?: return@postRoute
                val request = call.receive<CreatePostComplaintRequest>()
                postComplaintSignatureVerifier.verifyCreate(principal, request)
                postComplaintService.create(principal.gid, request)
                call.respond(HttpStatusCode.Created, null)
            }
            getRoute("/admin/post-complaints/count") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@getRoute
                call.respond(
                    HttpStatusCode.OK,
                    postComplaintService.countOpen(requesterGid),
                    typeInfo<OpenPostComplaintCountResult>(),
                )
            }
            getRoute("/admin/post-complaints") {
                val requesterGid = userGidOrRespondUnauthorized() ?: return@getRoute
                val page =
                    call.request.queryParameters["page"]?.let { value ->
                        value.toIntOrNull() ?: throw BadRequestException("page must be an integer")
                    } ?: 1
                call.respond(
                    HttpStatusCode.OK,
                    postComplaintService.listOpen(requesterGid, page),
                    typeInfo<OpenPostComplaintsResult>(),
                )
            }
            postRoute("/admin/post-complaints/decision") {
                val principal = userPrincipalOrRespondUnauthorized() ?: return@postRoute
                val request = call.receive<DecidePostComplaintRequest>()
                postComplaintSignatureVerifier.verifyDecision(principal, request)
                postComplaintService.decide(principal.gid, request)
                call.respond(HttpStatusCode.NoContent, null)
            }
        }
    }
}
