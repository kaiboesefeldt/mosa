package straightway.mosa

import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.onClose
import straightway.mosa.auth.Auth0TokenUtil
import straightway.mosa.auth.AuthenticationController
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.auth.FileBackedEd25519Factory
import straightway.mosa.auth.PodCertificateService
import straightway.mosa.auth.ThreadLocalEd25519VerifierProvider
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.defense.DefenseCommandLineOptions
import straightway.mosa.defense.approvegidRateLimit
import straightway.mosa.defense.configureDefenseAppModule
import straightway.mosa.defense.identityBlockList
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.configureVectorStoreAppModule
import straightway.mosa.webclient.WebClientController
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

typealias HasherFactory = () -> MessageDigest

fun Module.configureMosaAppModule(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY) {
    single<EmbeddingService> { OnnxLocalEmbeddingService.create() } onClose { (it as? AutoCloseable)?.close() }
    single { Clock.systemUTC() }
    single { NanoClock(System::nanoTime) }
    single { Random(get<NanoClock>().nanoTime()) }
    configureVectorStoreAppModule()
    single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
    single<SignatureFactory> { FileBackedEd25519Factory(get(), Paths.get("mosa_pod_keypair.pem")) }
    single<SignatureVerifierProvider> { ThreadLocalEd25519VerifierProvider() }
    single<Crypto> { CryptoEd25519() }
    single<TokenUtil> { Auth0TokenUtil(get(), get(), get(), get(), get()) }

    single {
        val cliArguments: CommandLineArgumentStore = get()
        val maxFetchItems = cliArguments.getInt(ServerCommandLineArguments.MAX_FETCH_ITEMS) ?: 100
        ContentService(get(), get(), get(), maxFetchItems)
    }

    single { PodCertificateService(get()) }

    single { arguments }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val raw = cliArguments.getString(ServerCommandLineArguments.TRUSTED_PROXY_IPS).orEmpty()
        val trustedProxyIps =
            raw
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        ClientIpResolver(trustedProxyIps)
    }

    single { ContentController(get(), get()) } bind BaseController::class
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val certificateLifetimeSeconds =
            cliArguments.getInt(DefenseCommandLineOptions.POD_CERT_VALIDITY_SECONDS)
                ?: 7.days.inWholeSeconds.toInt()
        AuthenticationController(
            get(),
            get(),
            certificateLifetimeSeconds.toLong().seconds,
            get(approvegidRateLimit),
            get(),
            get(identityBlockList),
        )
    } bind BaseController::class
    single {
        val cliArguments: CommandLineArgumentStore = get()
        WebClientController(
            serveSourceMaps = cliArguments.getBoolean(ServerCommandLineArguments.SERVE_SOURCE_MAPS) == true,
            allowPlainHttp = cliArguments.getBoolean(ServerCommandLineArguments.ALLOW_HTTP) == true,
        )
    } bind BaseController::class

    configureDefenseAppModule()
}
