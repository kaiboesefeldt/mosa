package straightway.mosa.client

import kotlinx.browser.window
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
                        single { IdentityGenerator() }
                        factory { PodAccess(it[0]) }
                        single {
                            SubtleCrypto(
                                window.asDynamic().crypto?.subtle ?: error("WebCrypto not available")
                            )
                        }
                    },
                )
            }
        }
    }
}

