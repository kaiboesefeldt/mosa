package straightway.mosa.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import org.koin.ktor.plugin.koin
import straightway.mosa.TokenUtil

fun Application.installAuthServerPlugins() {
    val tokenUtil = koin().get<TokenUtil>()

    install(Authentication) {
        bearer {
            authenticate { credentials ->
                runCatching {
                    val jwt = tokenUtil.decode(credentials.token)
                    if (!tokenUtil.isValidMisJwt(jwt)) return@runCatching null
                    val cert = jwt.claims["cert"]?.asMap()?.toPodCertificate() ?: return@runCatching null
                    val publicKeyBase64 = jwt.claims["pub"]?.asString() ?: return@runCatching null
                    PodPrincipal(cert, publicKeyBase64)
                }.onFailure {
                    val reason = it::class.simpleName ?: "AuthenticationError"
                    logger.debug { "Authentication failed: $reason" }
                }.getOrNull()
            }
        }
    }
}

private val logger = KotlinLogging.logger {}
