package straightway.mosa.content

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.onClose
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.base.BaseController
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.content.ContentCommandLineOptions.Companion.MAX_FETCH_ITEMS
import straightway.mosa.content.complaint.PostComplaintController
import straightway.mosa.content.complaint.PostComplaintService
import straightway.mosa.content.complaint.PostComplaintSignatureVerifier
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.content.complaint.PostComplaintsTable
import straightway.mosa.content.complaint.SqlitePostComplaintStore
import straightway.mosa.content.snapshot.FileBackedFreezeSnapshotArchiveKeyStore
import straightway.mosa.content.snapshot.FreezeSnapshotActiveArchiveKeyProvider
import straightway.mosa.content.snapshot.FreezeSnapshotArchiveKeyStore
import straightway.mosa.content.snapshot.FreezeSnapshotCryptoService
import straightway.mosa.content.snapshot.FreezeSnapshotFormat
import straightway.mosa.content.snapshot.FreezeSnapshotNonceGenerator
import straightway.mosa.content.snapshot.FreezeSnapshotPolicy
import straightway.mosa.content.snapshot.FreezeSnapshotRestoreService
import straightway.mosa.content.snapshot.FreezeSnapshotRestoreValidator
import straightway.mosa.content.snapshot.FreezeSnapshotSelectionService
import straightway.mosa.content.snapshot.FreezeSnapshotService
import straightway.mosa.content.snapshot.FreezeSnapshotSizePolicy
import straightway.mosa.content.snapshot.SecureRandomFreezeSnapshotNonceGenerator
import straightway.mosa.defense.adminAllowList
import straightway.mosa.message.AdminMessageCleanupJob
import straightway.mosa.message.AdminMessageController
import straightway.mosa.message.AdminMessageService
import straightway.mosa.message.AdminMessageStore
import straightway.mosa.message.AdminMessagesTable
import straightway.mosa.message.SqliteAdminMessageStore
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

fun Module.configureContentAppModule() {
    single { FileBackedFreezeSnapshotArchiveKeyStore(defaultFreezeSnapshotArchiveKeyPath(), get()) }
    single<FreezeSnapshotArchiveKeyStore> { get<FileBackedFreezeSnapshotArchiveKeyStore>() }
    single<FreezeSnapshotActiveArchiveKeyProvider> { get<FileBackedFreezeSnapshotArchiveKeyStore>() }
    single { FreezeSnapshotSelectionService(get()) }
    single { FreezeSnapshotFormat() }
    single { FreezeSnapshotCryptoService(get(), get()) }
    single<FreezeSnapshotNonceGenerator> { SecureRandomFreezeSnapshotNonceGenerator() }
    single { FreezeSnapshotSizePolicy() }
    single { FreezeSnapshotRestoreValidator(get()) }
    single {
        FreezeSnapshotPolicy(
            podId = defaultFreezeSnapshotPodId(get(), get()),
        )
    }
    single {
        FreezeSnapshotService(
            selectionService = get(),
            activeArchiveKeyProvider = get<FreezeSnapshotActiveArchiveKeyProvider>(),
            nonceGenerator = get(),
            format = get(),
            cryptoService = get(),
            vectorStore = get(),
            clock = get(),
            policy = get(),
        )
    }
    single {
        FreezeSnapshotRestoreService(
            format = get(),
            cryptoService = get(),
            validator = get(),
            embeddingService = get(),
            vectorStore = get(),
            policy = get(),
        )
    }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(MAX_FETCH_ITEMS) ?: 100
        val admins = get<AutoRefreshingFileBackedContent<AdminAllowList>>(adminAllowList)
        ContentService(get(), get(), get(), maxFetchItems) { gid ->
            (admins.get() ?: AdminAllowList(emptyList())).adminGids.contains(gid)
        }
    }
    single(createdAtStart = true) {
        StructuredStateDatabase(defaultStructuredStateDatabasePath()).also {
            it.initialize(AdminMessagesTable, PostComplaintsTable)
        }
    }
    single<PostComplaintStore> { SqlitePostComplaintStore(get()) }
    single<AdminMessageStore> { SqliteAdminMessageStore(get()) }
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
    single { PrivacySnapshotController(get(), get(), get()) } bind BaseController::class
    single { AdminMessageController(get()) } bind BaseController::class
}

private fun defaultFreezeSnapshotPodId(
    signatureFactory: SignatureFactory,
    hasherFactory: HasherFactory,
): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
        hasherFactory().digest(signatureFactory.publicKey.encoded),
    )

private fun defaultFreezeSnapshotArchiveKeyPath(): Path = Path.of(System.getProperty("user.dir"), "mosa_snapshot_archive_keys.json")

private fun defaultStructuredStateDatabasePath(): Path = Path.of(System.getProperty("user.dir"), "mosa_db.sqlite")
