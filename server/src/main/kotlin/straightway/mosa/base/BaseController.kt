package straightway.mosa.base

import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

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

    companion object {
        const val CURRENT_API_VERSION = "/v1/"
        const val ROOT = "/"
    }
}
