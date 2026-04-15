package straightway.mosa.structuredstate

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.content.complaint.PostComplaintsTable
import straightway.mosa.identity.blocklist.BlockedIdentitiesTable
import straightway.mosa.message.AdminMessagesTable
import java.nio.file.Path

fun Module.configureStructuredStateModule() {
    single(createdAtStart = true) {
        StructuredStateDatabase(defaultStructuredStateDatabasePath()).also {
            it.initialize(AdminMessagesTable, PostComplaintsTable, BlockedIdentitiesTable)
        }
    } bind StructuredStateContext::class
}

private fun defaultStructuredStateDatabasePath(): Path = Path.of(System.getProperty("user.dir"), "mosa_db.sqlite")
