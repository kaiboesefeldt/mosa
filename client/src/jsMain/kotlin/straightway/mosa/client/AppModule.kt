package straightway.mosa.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.BrowserPrefs
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.CryptoExport
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.IdentityProvider
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.IdentityStore
import straightway.mosa.client.identity.IndexedDbIdentityStore
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.identity.IdentityTransferCodec
import straightway.mosa.client.identity.IdentityTransferService
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.logging.BrowserLogger
import straightway.mosa.client.logging.Logger
import straightway.mosa.identity.FictionalNameGenerator
import kotlinx.serialization.json.Json
import straightway.mosa.client.pod.PodAccessImpl

object AppModule : KoinComponent {
    fun start() {
        val alreadyStarted = runCatching { GlobalContext.get() }.isSuccess
        if (!alreadyStarted) {
            startKoin {
                modules(
                    module {
                        single { TimeService() }
                        single {
                            MisJwtService(
                                get(),
                                get(),
                                get()
                            )
                        }
                        single { IdentityGenerator(get(), get()) }
                        single<IdentityProvider> { get<IdentityGenerator>() }
                        single<IdentityStore> {
                            IndexedDbIdentityStore(
                                get(),
                                get(),
                                window.asDynamic().indexedDB,
                                get(),
                            )
                        }
                        single<IdentitySelectionRepository> { IdentityRepository(get(), get<IdentityProvider>()) }
                        single {
                            HttpClient {
                                install(ContentNegotiation) { json() }
                            }
                        }
                        factory<PodAccess> { PodAccessImpl(it[0], get()) }
                        single {
                            SubtleCrypto(
                                window.asDynamic().crypto?.subtle
                                    ?: error("WebCrypto not available")
                            )
                        }
                        single { CryptoExport(get()) }
                        single {
                            IdentityTransferCodec(
                                get(),
                                get(),
                                Json { prettyPrint = true },
                            )
                        }
                        single<IdentityTransferPort> { IdentityTransferService(get()) }
                        single { FictionalNameGenerator() }
                        single { BrowserPrefs() }
                        single { Translator(get<BrowserPrefs>().languages) }
                        single { DiceBearAvatarGenerator() }
                        single<Logger> { BrowserLogger() }
                    },
                )
            }
        }
    }
}
