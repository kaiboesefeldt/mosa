package straightway.mosa

import org.koin.core.module.Module
import straightway.mosa.auth.configureAuthAppModule
import straightway.mosa.content.configureContentAppModule
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.embedding.configureEmbeddingAppModule
import straightway.mosa.identity.blocklist.configureIdentityBlocklistModule
import straightway.mosa.identity.moderation.configureIdentityModerationModule
import straightway.mosa.identity.privacy.configureIdentityPrivacyModule
import straightway.mosa.legal.configureLegalAppModule
import straightway.mosa.network.configureNetworkAppModule
import straightway.mosa.structuredstate.configureStructuredStateModule
import straightway.mosa.vectorstore.configureVectorStoreAppModule
import straightway.mosa.webclient.configureWebClientAppModule
import java.time.Clock
import kotlin.random.Random

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single { arguments }

    configureGenericComponents()
    configureVectorStoreAppModule()
    configureNetworkAppModule()
    configureDefenseAppModule()
    configureAuthAppModule()
    configureStructuredStateModule()
    configureIdentityBlocklistModule()
    configureContentAppModule()
    configureIdentityPrivacyModule()
    configureIdentityModerationModule()
    configureLegalAppModule()
    configureEmbeddingAppModule()
    configureWebClientAppModule()
}

private fun Module.configureGenericComponents() {
    single { Clock.systemUTC() }
    single { NanoClock(System::nanoTime) }
    single { Random(get<NanoClock>().nanoTime()) }
}
