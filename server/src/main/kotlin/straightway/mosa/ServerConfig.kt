package straightway.mosa

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.koin.ktor.plugin.koin
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.auth.InvalidPodCertificateRequest
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.auth.toPodCertificate
import straightway.mosa.base.findPayloadTooLarge
import straightway.mosa.defense.installDefenseServerPlugins
import straightway.mosa.jackson.InstantJacksonSerializers
import java.util.TimeZone

fun Application.installNetworkServerPlugins() {
    val koin = koin()
    val tlsConfig = koin.get<TlsConfig>()
    val authConfig = koin.get<AuthConfig>()

    installDefenseServerPlugins()
    if (tlsConfig.requireTlsForPod) {
        install(createMosaRequireTlsPlugin(authConfig.clientIpResolver))
    }
    install(createMosaApiCorsPlugin(tlsConfig.requireTlsForPod, authConfig.clientIpResolver))
    install(StatusPages) {
        exception<InvalidPodCertificateRequest> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
        }
        exception<BadRequestException> { call, cause ->
            val findPayloadTooLargeException = cause.findPayloadTooLarge()
            if (findPayloadTooLargeException != null) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("message" to findPayloadTooLargeException.message))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
            }
        }
        exception<PayloadTooLargeException> { call, _ ->
            call.respond(HttpStatusCode.PayloadTooLarge)
        }
    }
    install(Authentication) {
        bearer {
            authenticate { credentials ->
                runCatching {
                    val jwt = authConfig.tokenUtil.decode(credentials.token)
                    if (!authConfig.tokenUtil.isValidMisJwt(jwt)) return@runCatching null
                    val cert = jwt.claims["cert"]?.asMap()?.toPodCertificate() ?: return@runCatching null
                    PodPrincipal(cert)
                }.onFailure {
                    val reason = it::class.simpleName ?: "AuthenticationError"
                    logger.debug { "Authentication failed: $reason" }
                }.getOrNull()
            }
        }
    }
    install(ContentNegotiation) {
        jackson {
            registerModule(JavaTimeModule())
            registerModule(InstantJacksonSerializers.createModule())
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }
}

fun createMosaApiCorsPlugin(
    requireTlsForPod: Boolean,
    clientIpResolver: ClientIpResolver,
): ApplicationPlugin<Unit> =
    createApplicationPlugin("MosaApiCors") {
        onCall { call ->
            call.appendCorsBaseHeaders()

            val path = call.request.path()

            if (!path.startsWith(API_ROUTE_PREFIX)) return@onCall

            when (call.request.httpMethod) {
                HttpMethod.Options -> handlePreflightRequest(call)

                HttpMethod.Post -> {
                    if (path.equals(APPROVE_GID_ROUTE, ignoreCase = true)) return@onCall
                    call
                        .secureEndpointViolation(requireTlsForPod, clientIpResolver)
                        ?.let { call.respond(it) }
                }

                HttpMethod.Get ->
                    call
                        .secureEndpointViolation(requireTlsForPod, clientIpResolver)
                        ?.let { call.respond(it) }

                else -> call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }

private fun createMosaRequireTlsPlugin(clientIpResolver: ClientIpResolver): ApplicationPlugin<Unit> =
    createApplicationPlugin("MosaRequireTls") {
        onCall { call ->
            if (!call.isSecureRequest(clientIpResolver)) {
                call.respond(HttpStatusCode.BadRequest)
                return@onCall
            }
        }
    }

private val logger = KotlinLogging.logger {}

private const val API_ROUTE_PREFIX = "/v1/"
private const val APPROVE_GID_ROUTE = "/v1/approvegid"
private const val ALLOWED_CORS_METHODS = "POST, GET, OPTIONS"
private const val ALLOWED_CORS_HEADERS = "Authorization, Content-Type"
private const val CORS_MAX_AGE_SECONDS = "7200"

private fun ApplicationCall.appendCorsBaseHeaders() {
    response.headers.append(HttpHeaders.AccessControlAllowOrigin, "*")
    response.headers.append(HttpHeaders.AccessControlExposeHeaders, "Retry-After")
}

private suspend fun handlePreflightRequest(call: ApplicationCall) {
    call.appendPreflightHeaders()
    call.respond(HttpStatusCode.NoContent)
}

private fun ApplicationCall.appendPreflightHeaders() {
    response.headers.append(HttpHeaders.AccessControlAllowMethods, ALLOWED_CORS_METHODS)
    response.headers.append(HttpHeaders.AccessControlAllowHeaders, ALLOWED_CORS_HEADERS)
    response.headers.append(HttpHeaders.AccessControlMaxAge, CORS_MAX_AGE_SECONDS)
}

private fun ApplicationCall.secureEndpointViolation(
    requireTlsForPod: Boolean,
    clientIpResolver: ClientIpResolver,
): HttpStatusCode? {
    if (requireTlsForPod && !isSecureRequest(clientIpResolver)) {
        return HttpStatusCode.BadRequest
    }

    val authorizationHeader = request.headers[HttpHeaders.Authorization]
    if (authorizationHeader.isNullOrBlank()) {
        return HttpStatusCode.Unauthorized
    }

    return null
}

private fun ApplicationCall.isSecureRequest(clientIpResolver: ClientIpResolver): Boolean {
    val forwardedProto = request.headers[HttpHeaders.XForwardedProto]
    if (
        clientIpResolver.isTrustedProxy(request.local.remoteHost) &&
        forwardedProto.equals("https", ignoreCase = true)
    ) {
        return true
    }

    return request.local.scheme.equals("https", ignoreCase = true)
}
