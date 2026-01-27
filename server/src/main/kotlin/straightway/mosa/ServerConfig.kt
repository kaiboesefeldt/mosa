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
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import org.koin.ktor.plugin.koin
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.auth.InvalidPodCertificateRequest
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.auth.toPodCertificate
import straightway.mosa.base.findPayloadTooLarge
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.config.contains
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.createMosaLoadSheddingPlugin
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.createMosaRequestBodyLimitPlugin
import straightway.mosa.identity.IdentityLogEntryFormatter
import straightway.mosa.jackson.InstantJacksonSerializers
import java.net.InetAddress
import java.util.TimeZone

fun Application.installNetworkServerPlugins() {
    val koin = koin()
    val tlsConfig = koin.get<TlsConfig>()
    val authConfig = koin.get<AuthConfig>()
    val requestBodyLimitConfig = koin.get<RequestBodyLimitConfig>()
    val ipBlockList = koin.get<AutoRefreshingFileBackedContent<ClientBlockList>>(ipBlockList)
    val ipRateLimiter = koin.get<RateLimiter>(ipRateLimit)

    install(RequestMetricsPlugin())
    install(createMosaLoadSheddingPlugin())
    install(createMosaIpBlockListPlugin(authConfig.clientIpResolver, ipBlockList))
    install(createMosaIpRateLimitPlugin(ipRateLimiter, authConfig.clientIpResolver))
    install(createMosaRequestBodyLimitPlugin(requestBodyLimitConfig))
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

fun createMosaAuthenticatedRateLimitPlugin(rateLimiter: RateLimiter) =
    createRouteScopedPlugin("MosaAuthenticatedRateLimit") {
        on(AuthenticationChecked) { call ->
            val principal = call.principal<PodPrincipal>() ?: return@on
            val decision = rateLimiter.check(principal.gid)
            if (decision.allowed) return@on
            logger.warn { "rate limit exceeded for ${IdentityLogEntryFormatter.format(principal.gid)}" }
            call.response.headers.append(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
            call.respond(
                HttpStatusCode.TooManyRequests,
                mapOf("message" to AUTHENTICATED_RATE_LIMIT_MESSAGE),
            )
        }
    }

fun createMosaAuthenticatedIdentityBlockListPlugin(identityBlockList: AutoRefreshingFileBackedContent<IdentityBlockList>) =
    createRouteScopedPlugin("MosaIdentityBlockList") {
        on(AuthenticationChecked) { call ->
            val principal = call.principal<PodPrincipal>() ?: return@on
            val blockList = identityBlockList.get() ?: return@on
            if (principal.gid !in blockList) return@on
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to IDENTITY_BLOCKED_MESSAGE))
        }
    }

fun createMosaIpRateLimitPlugin(
    rateLimiter: RateLimiter,
    clientIpResolver: ClientIpResolver,
): Plugin<Application, Unit, Unit> =
    object : Plugin<Application, Unit, Unit> {
        override val key = AttributeKey<Unit>("MosaIpRateLimit")

        override fun install(
            pipeline: Application,
            configure: Unit.() -> Unit,
        ) {
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val decision = rateLimiter.check(call.clientIpAddress(clientIpResolver))
                if (decision.allowed) return@intercept
                call.response.headers.append(HttpHeaders.RetryAfter, decision.retryAfterSeconds.toString())
                call.respond(
                    HttpStatusCode.TooManyRequests,
                    mapOf("message" to IP_RATE_LIMIT_MESSAGE),
                )
                finish()
            }
        }
    }

fun createMosaIpBlockListPlugin(
    clientIpResolver: ClientIpResolver,
    ipBlockList: AutoRefreshingFileBackedContent<ClientBlockList>,
): Plugin<Application, Unit, Unit> =
    object : Plugin<Application, Unit, Unit> {
        override val key = AttributeKey<Unit>("MosaIpBlockList")

        override fun install(
            pipeline: Application,
            configure: Unit.() -> Unit,
        ) {
            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val blockList = ipBlockList.get() ?: return@intercept
                val address =
                    runCatching { InetAddress.getByName(call.clientIpAddress(clientIpResolver)) }.getOrNull()
                        ?: return@intercept
                if (address !in blockList) return@intercept
                call.respond(HttpStatusCode.Forbidden, mapOf("message" to CLIENT_BLOCKED_MESSAGE))
                finish()
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
private const val AUTHENTICATED_RATE_LIMIT_MESSAGE = "Rate limit exceeded"
private const val IP_RATE_LIMIT_MESSAGE = "Rate limit exceeded"
private const val CLIENT_BLOCKED_MESSAGE = "Client blocked"
private const val IDENTITY_BLOCKED_MESSAGE = "Identity blocked"
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

private fun ApplicationCall.clientIpAddress(clientIpResolver: ClientIpResolver): String =
    clientIpResolver.resolve(
        remoteHost = request.local.remoteHost,
        forwardedFor = request.headers[HttpHeaders.XForwardedFor],
    )
