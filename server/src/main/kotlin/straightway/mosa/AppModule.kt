package straightway.mosa

import org.koin.core.module.Module
import straightway.mosa.auth.configureAuthAppModule
import straightway.mosa.content.configureContentAppModule
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.embedding.configureEmbeddingAppModule
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.vectorstore.configureVectorStoreAppModule
import straightway.mosa.webclient.configureWebClientAppModule
import java.time.Clock
import kotlin.random.Random

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single { arguments }
    single { Clock.systemUTC() }
    single { NanoClock(System::nanoTime) }
    single { Random(get<NanoClock>().nanoTime()) }

    configureVectorStoreAppModule()
    configureNetworkAppModule()
    configureDefenseAppModule()
    configureAuthAppModule()
    configureContentAppModule()
    configureEmbeddingAppModule()
    configureWebClientAppModule()
}
