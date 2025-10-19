package straightway.mosa

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import straightway.mosa.jackson.InstantJacksonSerializers
import java.util.TimeZone

fun Application.installNetworkServerPlugins(tokenUtil: TokenUtil) {
    install(
        createApplicationPlugin("MosaApiCors") {
            val allowedMethods = "POST, GET, OPTIONS"
            val allowedHeaders = "Authorization, Content-Type"
            val maxAgeSeconds = "7200"

            onCall { call ->
                call.response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
                call.response.headers.append(HttpHeaders.AccessControlExposeHeaders, "Retry-After")

                val path = call.request.path()

                if (!path.startsWith("/v1/")) return@onCall

                if (call.request.httpMethod == HttpMethod.Options) {
                    call.response.headers.append(HttpHeaders.AccessControlAllowMethods, allowedMethods)
                    call.response.headers.append(HttpHeaders.AccessControlAllowHeaders, allowedHeaders)
                    call.response.headers.append(HttpHeaders.AccessControlMaxAge, maxAgeSeconds)
                    call.respond(HttpStatusCode.NoContent)
                    return@onCall
                }

                val isOpenEndpoint = path.equals("/v1/approvegid", ignoreCase = true)

                if (call.request.httpMethod != HttpMethod.Post && call.request.httpMethod != HttpMethod.Get) {
                    call.respond(HttpStatusCode.MethodNotAllowed)
                    return@onCall
                }

                if (!isOpenEndpoint) {
                    val scheme =
                        call.request.headers[HttpHeaders.XForwardedProto]
                            ?: call.request.local.scheme
                    if (!scheme.equals("https", ignoreCase = true)) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@onCall
                    }

                    val authorizationHeader = call.request.headers[HttpHeaders.Authorization]
                    if (authorizationHeader.isNullOrBlank()) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@onCall
                    }
                }
            }
        },
    )
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
                    val reason = it::class.simpleName ?: "AuthenticationError"
                    log.debug { "Authentication failed: $reason" }
                }.getOrNull()
            }
        }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            registerModule(InstantJacksonSerializers.createModule())
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }
}

private val log = KotlinLogging.logger {}
