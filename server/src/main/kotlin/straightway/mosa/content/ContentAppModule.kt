package straightway.mosa.content

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentCommandLineOptions.Companion.MAX_FETCH_ITEMS

fun Module.configureContentAppModule() {
    single { FreezeSnapshotSelectionService(get()) }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(MAX_FETCH_ITEMS) ?: 100
        ContentService(get(), get(), get(), maxFetchItems)
    }
    single { ContentController(get(), get()) } bind BaseController::class
}
