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
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.micrometer.core.instrument.MeterRegistry
import org.koin.core.Koin
import straightway.mosa.auth.InvalidPodCertificateRequest
import straightway.mosa.defense.CombinedSheddingDecider
import straightway.mosa.defense.CpuSheddingDecider
import straightway.mosa.defense.MemorySheddingDecider
import straightway.mosa.defense.RequestDurationSheddingDecider
import straightway.mosa.defense.RequestMetricsPlugin
import straightway.mosa.defense.createMosaLoadSheddingPlugin
import straightway.mosa.jackson.InstantJacksonSerializers
import java.util.TimeZone
import kotlin.random.Random
import kotlin.time.Duration

fun Application.installNetworkServerPlugins(
    koin: Koin,
    requireTlsForPod: Boolean,
    loadSheddingThreshold: Double,
    memorySheddingThreshold: Double,
    loadSheddingRetryAfter: Duration,
) {
    val meterRegistry = koin.get<MeterRegistry>()
    install(RequestMetricsPlugin(meterRegistry))
    install(createMosaLoadSheddingPlugin(Random(System.nanoTime()))) {
        decider =
            CombinedSheddingDecider(
                listOf(
                    CpuSheddingDecider(loadSheddingThreshold = loadSheddingThreshold),
                    MemorySheddingDecider(memorySheddingThreshold = memorySheddingThreshold),
                    RequestDurationSheddingDecider(meterRegistry),
                ),
            )
        retryAfter = loadSheddingRetryAfter
    }
    if (requireTlsForPod) {
        install(createMosaRequireTlsPlugin())
    }
    install(createMosaApiCorsPlugin(requireTlsForPod))
    install(StatusPages) {
        exception<InvalidPodCertificateRequest> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
        }
    }
    install(Authentication.Companion) {
        bearer {
            authenticate { credentials ->
                runCatching {
                    val tokenUtil: TokenUtil = koin.get()
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
            setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            setTimeZone(TimeZone.getTimeZone("UTC"))
        }
    }
}

fun createMosaApiCorsPlugin(requireTlsForPod: Boolean): ApplicationPlugin<Unit> =
    createApplicationPlugin("MosaApiCors") {
        onCall { call ->
            call.appendCorsBaseHeaders()

            val path = call.request.path()

            if (!path.startsWith(API_ROUTE_PREFIX)) return@onCall

            when (call.request.httpMethod) {
                HttpMethod.Options -> handlePreflightRequest(call)

                HttpMethod.Post -> {
                    if (path.equals(APPROVE_GID_ROUTE, ignoreCase = true)) return@onCall
                    call.secureEndpointViolation(requireTlsForPod)?.let { call.respond(it) }
                }

                HttpMethod.Get -> call.secureEndpointViolation(requireTlsForPod)?.let { call.respond(it) }

                else -> call.respond(HttpStatusCode.MethodNotAllowed)
            }
        }
    }

private fun createMosaRequireTlsPlugin(): ApplicationPlugin<Unit> =
    createApplicationPlugin("MosaRequireTls") {
        onCall { call ->
            if (!call.isSecureRequest()) {
                call.respond(HttpStatusCode.BadRequest)
                return@onCall
            }
        }
    }

private val log = KotlinLogging.logger {}

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

private fun ApplicationCall.secureEndpointViolation(requireTlsForPod: Boolean): HttpStatusCode? {
    if (requireTlsForPod && !isSecureRequest()) {
        return HttpStatusCode.BadRequest
    }

    val authorizationHeader = request.headers[HttpHeaders.Authorization]
    if (authorizationHeader.isNullOrBlank()) {
        return HttpStatusCode.Unauthorized
    }

    return null
}

private fun ApplicationCall.isSecureRequest(): Boolean {
    val forwardedProto = request.headers[HttpHeaders.XForwardedProto]
    if (forwardedProto.equals("https", ignoreCase = true)) {
        return true
    }

    return request.local.scheme.equals("https", ignoreCase = true)
}
