package straightway.mosa.identity.blocklist

import org.koin.core.module.Module
import straightway.mosa.structuredstate.StructuredStateDatabase

fun Module.configureIdentityBlocklistModule() {
    single<BlockedIdentityStore> { SqliteBlockedIdentityStore(get<StructuredStateDatabase>()) }
}
