package straightway.mosa

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import straightway.mosa.base.BaseController
import straightway.mosa.base.register

suspend fun main() {
    val server =
        embeddedServer(Netty, port = 8080) {
            install(Koin) {
                modules(appModule)
            }
            installNetworkServerPlugins(getKoin().get())
            routing {
                getKoin().getAll<BaseController>().forEach { register(it) }
            }
        }

    server.startSuspend(wait = true)
}
