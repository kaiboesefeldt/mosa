package straightway.mosa

import com.fasterxml.jackson.annotation.JsonInclude.Include
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.koin.dsl.module
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import straightway.mosa.base.register
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.embedding.DefaultEmbeddingService
import straightway.mosa.vectorstore.DefaultVectorStore

suspend fun main() {
    val server =
        embeddedServer(Netty, port = 8080) {
            install(Authentication) {
                bearer {
                    authenticate { credentials ->
                        UserIdPrincipal(credentials.token)
                    }
                }
            }
            install(ContentNegotiation) {
                jackson {
                    setSerializationInclusion(Include.NON_NULL)
                }
            }
            install(Koin) {
                modules(appModule)
            }
            routing {
                register(get<ContentController>())
            }
        }

    server.startSuspend(wait = true)
}

private val appModule =
    module {
        single<EmbeddingService> { DefaultEmbeddingService() }
        single<VectorStore> { DefaultVectorStore() }
        single<ContentService> { ContentService() }

        factory { ContentController(get()) }
    }
