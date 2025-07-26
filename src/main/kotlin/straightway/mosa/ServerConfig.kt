package straightway.mosa

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import java.util.TimeZone

fun Application.installNetworkServerPlugins(tokenUtil: TokenUtil) {
    install(Authentication.Companion) {
        bearer {
            authenticate { credentials ->
                runCatching {
                    val jwt = tokenUtil.decode(credentials.token)
                    if (!tokenUtil.isValidMisJwt(jwt)) return@runCatching null
                    val cert = jwt.claims["cert"]?.asMap()
                    val userGID = cert?.let { it["gid"] }?.toString()
                    userGID?.let { UserIdPrincipal(userGID) }
                }.onFailure {
                    log.debug(it) { "Authentication failed" }
                }.getOrNull()
            }
        }
    }
    install(ContentNegotiation) {
        jackson {
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }
}

private val log = KotlinLogging.logger {}
