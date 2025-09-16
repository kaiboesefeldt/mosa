package straightway.mosa.client

import kotlinx.browser.window
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

object ClientDI {
    fun start() {
        val alreadyStarted = runCatching { GlobalContext.get() }.isSuccess
        if (!alreadyStarted) {
            startKoin {
                modules(
                    module {
                        single { TimeService() }
                        single { MisJwtService(get(), get(), get()) }
                        single { IdentityGenerator(get(), get()) }
                        single {
                            HttpClient {
                                install(ContentNegotiation) { json() }
                            }
                        }
                        factory { PodAccess(it[0], get()) }
                        single {
                            SubtleCrypto(
                                window.asDynamic().crypto?.subtle ?: error("WebCrypto not available")
                            )
                        }
                        single { CryptoExport(get()) }
                    },
                )
            }
        }
    }
}
