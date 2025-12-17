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
    abstract fun Route.register()

    fun Route.postRoute(
        path: String,
        build: suspend RoutingContext.() -> Unit,
    ) = route("/v1/$path") {
        post {
            build()
        }
    }

    fun registerAt(routing: Routing) {
        routing.register()
    }
}
