package straightway.mosa

import io.ktor.server.application.Application
import straightway.mosa.auth.installAuthServerPlugins
import straightway.mosa.defense.installDefenseServerPlugins
import straightway.mosa.network.installNetworkServerPlugins

fun Application.installKtorPlugins() {
    installDefenseServerPlugins()
    installAuthServerPlugins()
    installNetworkServerPlugins()
}
