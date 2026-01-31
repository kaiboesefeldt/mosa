package straightway.mosa

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.auth.configureAuthAppModule
import straightway.mosa.base.BaseController
import straightway.mosa.content.configureContentAppModule
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.dev.DevCommandLineOptions.Companion.SERVE_SOURCE_MAPS
import straightway.mosa.embedding.configureEmbeddingAppModule
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.vectorstore.configureVectorStoreAppModule
import straightway.mosa.webclient.WebClientController
import java.time.Clock
import kotlin.random.Random

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single { Clock.systemUTC() }
    single { NanoClock(System::nanoTime) }
    single { Random(get<NanoClock>().nanoTime()) }
    configureVectorStoreAppModule()
    single { arguments }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        WebClientController(
            serveSourceMaps = cliArguments.getBoolean(SERVE_SOURCE_MAPS) == true,
            allowPlainHttp = cliArguments.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP) == true,
        )
    } bind BaseController::class

    configureNetworkAppModule()
    configureDefenseAppModule()
    configureAuthAppModule()
    configureContentAppModule()
    configureEmbeddingAppModule()
}
