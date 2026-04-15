package straightway.mosa.identity.privacy

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.base.BaseController
import straightway.mosa.identity.privacy.snapshot.FileBackedFreezeSnapshotArchiveKeyStore
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotActiveArchiveKeyProvider
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotArchiveKeyStore
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotCryptoService
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotFormat
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotNonceGenerator
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotPolicy
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotRestoreService
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotRestoreValidator
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotSelectionService
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotService
import straightway.mosa.identity.privacy.snapshot.FreezeSnapshotSizePolicy
import straightway.mosa.identity.privacy.snapshot.SecureRandomFreezeSnapshotNonceGenerator
import straightway.mosa.structuredstate.StructuredStateContext
import java.nio.file.Path
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.days

fun Module.configureIdentityPrivacyModule() {
    single { FileBackedFreezeSnapshotArchiveKeyStore(defaultFreezeSnapshotArchiveKeyPath(), get()) }
    single<FreezeSnapshotArchiveKeyStore> { get<FileBackedFreezeSnapshotArchiveKeyStore>() }
    single<FreezeSnapshotActiveArchiveKeyProvider> { get<FileBackedFreezeSnapshotArchiveKeyStore>() }
    single { FreezeSnapshotSelectionService(get(), get<StructuredStateContext>(), get(), get()) }
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
            database = get(),
            postComplaintStoreFactory = get(),
            adminMessageStoreFactory = get(),
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
            database = get(),
            postComplaintStoreFactory = get(),
            adminMessageStoreFactory = get(),
            vectorStore = get(),
            clock = get(),
            adminMessageUnretrievedTtl = 180.days,
            policy = get(),
        )
    }
    single { IdentityDeletionService(get(), get(), get(), get(), get()) } bind IdentityDeleter::class
    single { IdentityDeletionController(get()) } bind BaseController::class
    single { PrivacySnapshotController(get(), get(), get()) } bind BaseController::class
}

private fun defaultFreezeSnapshotPodId(
    signatureFactory: SignatureFactory,
    hasherFactory: HasherFactory,
): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
        hasherFactory().digest(signatureFactory.publicKey.encoded),
    )

private fun defaultFreezeSnapshotArchiveKeyPath(): Path = Path.of(System.getProperty("user.dir"), "mosa_snapshot_archive_keys.json")
