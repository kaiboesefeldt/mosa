package straightway.mosa

import com.fasterxml.jackson.annotation.JsonInclude
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun Application.installNetworkServerPlugins() {
    install(Authentication.Companion) {
        bearer {
            authenticate { credentials ->
                UserIdPrincipal(credentials.token)
            }
        }
    }
    install(ContentNegotiation) {
        jackson {
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
    }
}
