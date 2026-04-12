package straightway.mosa.auth

import org.koin.core.module.Module
import org.koin.dsl.bind
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.Crypto
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.TokenUtil
import straightway.mosa.base.BaseController
import straightway.mosa.content.BlockedIdentityStore
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.defense.approvegidRateLimit
import straightway.mosa.network.NetworkCommandLineOptions
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

fun Module.configureAuthAppModule() {
    single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
    single<SignatureFactory> { FileBackedEd25519Factory(get(), Paths.get("mosa_pod_keypair.pem")) }
    single<SignatureVerifierProvider> { ThreadLocalEd25519VerifierProvider() }
    single<Crypto> { CryptoEd25519() }
    single<TokenUtil> { Auth0TokenUtil(get(), get(), get(), get(), get()) }
    single { PodCertificateService(get()) }
    single {
        val cliArguments: CommandLineArgumentStore = get()
        val certificateLifetimeSeconds =
            cliArguments.getInt(NetworkCommandLineOptions.POD_CERT_VALIDITY_SECONDS)
                ?: 7.days.inWholeSeconds.toInt()
        AuthenticationController(
            get(),
            get(),
            certificateLifetimeSeconds.toLong().seconds,
            get(approvegidRateLimit),
            get(),
            get<BlockedIdentityStore>(),
        )
    } bind BaseController::class
}
