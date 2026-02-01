package straightway.mosa.webclient

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.base.BaseController
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.webclient.WebClientCommandLineOptions.Companion.SERVE_SOURCE_MAPS

fun Module.configureWebClientAppModule() {
    single {
        val cliArguments: CommandLineArgumentStore = get()
        WebClientController(
            serveSourceMaps = cliArguments.getBoolean(SERVE_SOURCE_MAPS) == true,
            allowPlainHttp = cliArguments.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP) == true,
        )
    } bind BaseController::class
}
