package straightway.mosa.base

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import straightway.mosa.auth.PodCertificate
import straightway.mosa.auth.PodPrincipal

fun Routing.register(controller: BaseController) {
    controller.registerAt(this)
}

abstract class BaseController(
    val pathPrefix: String = CURRENT_API_VERSION,
) {
    abstract fun Route.register()

    fun Route.postRoute(
        path: String,
        build: suspend RoutingContext.() -> Unit,
    ) = route("$pathPrefix$path") {
        post {
            build()
        }
    }

    fun Route.getRoute(
        path: String,
        build: suspend RoutingContext.() -> Unit,
    ) = route("$pathPrefix$path") {
        get {
            build()
        }
    }

    fun registerAt(routing: Routing) {
        routing.register()
    }

    protected suspend fun RoutingContext.userGidOrRespondUnauthorized(): String? {
        val principal =
            runCatching {
                call.principal<PodPrincipal>()?.gid
                    ?: error("Missing authenticated principal for ${call.request.path()}")
            }.getOrElse {
                val description = it.message ?: "Missing authenticated principal"
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to description))
                return null
            }
        return principal
    }

    protected suspend fun RoutingContext.userPodCertificateOrRespondUnauthorized(): PodCertificate? {
        val certificate =
            runCatching {
                call.principal<PodPrincipal>()?.certificate
                    ?: error("Missing authenticated certificate for ${call.request.path()}")
            }.getOrElse {
                val description = it.message ?: "Missing authenticated certificate"
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to description))
                return null
            }
        return certificate
    }

    companion object {
        const val CURRENT_API_VERSION = "/v1/"
        const val ROOT = "/"
    }
}
