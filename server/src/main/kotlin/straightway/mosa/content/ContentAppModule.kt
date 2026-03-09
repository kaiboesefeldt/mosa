package straightway.mosa.content

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentCommandLineOptions.Companion.MAX_FETCH_ITEMS
import straightway.mosa.content.snapshot.FreezeSnapshotActiveArchiveKeyProvider
import straightway.mosa.content.snapshot.FreezeSnapshotCryptoService
import straightway.mosa.content.snapshot.FreezeSnapshotFormat
import straightway.mosa.content.snapshot.FreezeSnapshotNonceGenerator
import straightway.mosa.content.snapshot.FreezeSnapshotPolicy
import straightway.mosa.content.snapshot.FreezeSnapshotSelectionService
import straightway.mosa.content.snapshot.FreezeSnapshotService
import straightway.mosa.content.snapshot.SecureRandomFreezeSnapshotNonceGenerator
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.days

fun Module.configureContentAppModule() {
    single { FreezeSnapshotSelectionService(get()) }
    single { FreezeSnapshotFormat() }
    single { FreezeSnapshotCryptoService(get(), get()) }
    single<FreezeSnapshotNonceGenerator> { SecureRandomFreezeSnapshotNonceGenerator() }
    single {
        FreezeSnapshotPolicy(
            podId = defaultFreezeSnapshotPodId(get(), get()),
            snapshotLifetime = 365.days,
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
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(MAX_FETCH_ITEMS) ?: 100
        ContentService(get(), get(), get(), maxFetchItems)
    }
    single { ContentController(get(), get()) } bind BaseController::class
}

private fun defaultFreezeSnapshotPodId(
    signatureFactory: SignatureFactory,
    hasherFactory: HasherFactory,
): String =
    Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
        hasherFactory().digest(signatureFactory.publicKey.encoded),
    )
