package straightway.mosa.client

import DeterministicNameGenerator
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.window
import org.koin.core.component.KoinComponent
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import straightway.mosa.client.aux.TimeService
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.identity.CryptoExport
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.FictionalNameGenerator
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.pod.PodAccess

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
                        single {
                            HttpClient {
                                install(ContentNegotiation) { json() }
                            }
                        }
                        factory { PodAccess(it[0], get()) }
                        single {
                            SubtleCrypto(
                                window.asDynamic().crypto?.subtle
                                    ?: error("WebCrypto not available")
                            )
                        }
                        single { CryptoExport(get()) }
                        single { DeterministicNameGenerator() }
                        single { FictionalNameGenerator() }
                    },
                )
            }
        }
    }
}