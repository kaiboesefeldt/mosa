package straightway.mosa

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.onClose
import straightway.mosa.auth.configureAuthAppModule
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.network.NetworkCommandLineOptions
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.vectorstore.configureVectorStoreAppModule
import straightway.mosa.webclient.WebClientController
import java.time.Clock
import kotlin.random.Random

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single<EmbeddingService> { OnnxLocalEmbeddingService.create() } onClose { (it as? AutoCloseable)?.close() }
    single { Clock.systemUTC() }
    single { NanoClock(System::nanoTime) }
    single { Random(get<NanoClock>().nanoTime()) }
    configureVectorStoreAppModule()
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS) ?: 100
        ContentService(get(), get(), get(), maxFetchItems)
    }
    single { arguments }
    single { ContentController(get(), get()) } bind BaseController::class
    single {
        val cliArguments: CommandLineArgumentStore = get()
        WebClientController(
            serveSourceMaps = cliArguments.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS) == true,
            allowPlainHttp = cliArguments.getBoolean(NetworkCommandLineOptions.ALLOW_HTTP) == true,
        )
    } bind BaseController::class

    configureNetworkAppModule()
    configureDefenseAppModule()
    configureAuthAppModule()
}
