package straightway.mosa.content

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.onClose
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.HasherFactory
import straightway.mosa.base.BaseController
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.content.ContentCommandLineOptions.Companion.MAX_FETCH_ITEMS
import straightway.mosa.content.complaint.PostComplaintController
import straightway.mosa.content.complaint.PostComplaintService
import straightway.mosa.content.complaint.PostComplaintSignatureVerifier
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.content.complaint.SqlitePostComplaintStore
import straightway.mosa.defense.adminAllowList
import straightway.mosa.identity.blocklist.BlockedIdentityStore
import straightway.mosa.message.AdminMessageCleanupJob
import straightway.mosa.message.AdminMessageController
import straightway.mosa.message.AdminMessageService
import straightway.mosa.message.AdminMessageStore
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.message.SqliteAdminMessageStore
import straightway.mosa.structuredstate.StructuredStateDatabase
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

fun Module.configureContentAppModule() {
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(MAX_FETCH_ITEMS) ?: 100
        val admins = get<AutoRefreshingFileBackedContent<AdminAllowList>>(adminAllowList)
        ContentService(
            embeddingService = get(),
            vectorStore = get(),
            hasherFactory = get(),
            maxFetchItems = maxFetchItems,
            isAdmin = { gid -> (admins.get() ?: AdminAllowList(emptyList())).adminGids.contains(gid) },
        )
    }
    single<PostComplaintStoreFactory> { PostComplaintStoreFactory { context -> SqlitePostComplaintStore(context) } }
    single<AdminMessageStoreFactory> { AdminMessageStoreFactory { context -> SqliteAdminMessageStore(context) } }
    single<PostComplaintStore> { get<PostComplaintStoreFactory>().create(get<StructuredStateDatabase>()) }
    single<AdminMessageStore> { get<AdminMessageStoreFactory>().create(get<StructuredStateDatabase>()) }
    single {
        val admins = get<AutoRefreshingFileBackedContent<AdminAllowList>>(adminAllowList)
        AdminMessageService(
            store = get(),
            clock = get(),
            isAdmin = { gid -> (admins.get() ?: AdminAllowList(emptyList())).adminGids.contains(gid) },
            unretrievedTtl = 180.days,
            retrievedTtl = 28.days,
            pageSize = 10,
            idFactory = {
                java.util.UUID
                    .randomUUID()
                    .toString()
            },
            expectedGidByteLength = get<HasherFactory>()().digest(byteArrayOf()).size,
        )
    }
    single {
        val admins = get<AutoRefreshingFileBackedContent<AdminAllowList>>(adminAllowList)
        PostComplaintService(
            store = get(),
            contentService = get(),
            adminMessageService = get(),
            clock = get(),
            isAdmin = { gid -> (admins.get() ?: AdminAllowList(emptyList())).adminGids.contains(gid) },
            idFactory = {
                java.util.UUID
                    .randomUUID()
                    .toString()
            },
            openPageSize = 10,
        )
    }
    single(createdAtStart = true) { AdminMessageCleanupJob(get(), cleanupInterval = 6.hours) } onClose { it?.close() }
    single { ContentSignatureVerifier(get(), get(), get()) }
    single { PostComplaintSignatureVerifier(get(), get(), get()) }
    single { ContentController(get(), get(), get()) } bind BaseController::class
    single { PostComplaintController(get(), get()) } bind BaseController::class
    single { AdminMessageController(get()) } bind BaseController::class
}
