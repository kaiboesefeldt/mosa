package straightway.mosa.base

import io.ktor.server.routing.Route
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Routing.register(controller: BaseController) {
    controller.registerAt(this)
}

abstract class BaseController {
    abstract fun Routing.register(): Unit

    fun Route.postRouteAuthorized(path: String, build: suspend RoutingContext.() -> Unit) =
        route("/v1/$path") {
            post {
                authorize()
                build()
            }
        }

    fun RoutingContext.authorize(): Unit {
        // TODO
    }

    fun registerAt(routing: Routing) {
        routing.register()
    }
}