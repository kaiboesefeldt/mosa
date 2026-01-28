package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.module.Module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.ServerCommandLineArguments

fun Module.configureDefenseRuntimeModule(argumentStore: CommandLineArgumentStore) {
    val maxFetchItems = argumentStore.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS) ?: 100
    val loadSheddingConfig = buildLoadSheddingConfig(argumentStore)
    val requestBodyLimitConfig = buildRequestBodyLimitConfig(argumentStore, maxFetchItems)
    if (argumentStore.getInt(DefenseCommandLineOptions.MAX_REQUEST_BODY_BYTES) == null) {
        klogger.info { "Actual Max Request Body Bytes: ${requestBodyLimitConfig.maxBodyBytes}" }
    }
    single { loadSheddingConfig }
    single { requestBodyLimitConfig }
}

private val klogger = KotlinLogging.logger {}
