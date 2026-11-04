package straightway.mosa.network

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.koin.ktor.plugin.koin
import straightway.mosa.TlsConfig
import straightway.mosa.auth.InvalidPodCertificateRequest
import straightway.mosa.base.findPayloadTooLarge
import straightway.mosa.content.ContentService.ContentForbiddenException
import straightway.mosa.content.ContentService.ContentNotFoundException
import straightway.mosa.content.UnprocessableContentException
import straightway.mosa.content.complaint.PostComplaintConflictException
import straightway.mosa.content.complaint.PostComplaintForbiddenException
import straightway.mosa.identity.moderation.IdentityModerationService.IdentityModerationConflictException
import straightway.mosa.identity.moderation.IdentityModerationService.IdentityModerationForbiddenException
import straightway.mosa.identity.privacy.snapshot.InvalidFreezeSnapshotException
import straightway.mosa.jackson.InstantJacksonSerializers
import straightway.mosa.message.AdminMessageService.AdminMessageForbiddenException
import java.util.TimeZone

fun Application.installNetworkServerPlugins() {
    val koin = koin()
    val tlsConfig = koin.get<TlsConfig>()
    val clientIpResolver = koin.get<ClientIpResolver>()

    if (tlsConfig.requireTlsForPod) {
        install(createMosaRequireTlsPlugin(clientIpResolver))
    }
    install(createMosaApiCorsPlugin(tlsConfig.requireTlsForPod, clientIpResolver))
    install(StatusPages) {
        exception<InvalidPodCertificateRequest> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
        }
        exception<ContentNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("message" to cause.message))
        }
        exception<ContentForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to cause.message))
        }
        exception<AdminMessageForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to cause.message))
        }
        exception<IdentityModerationForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to cause.message))
        }
        exception<IdentityModerationConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("message" to cause.message))
        }
        exception<PostComplaintForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to cause.message))
        }
        exception<PostComplaintConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("message" to cause.message))
        }
        exception<InvalidFreezeSnapshotException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
        }
        exception<UnprocessableContentException> { call, cause ->
            call.respond(HttpStatusCode.UnprocessableEntity, mapOf("message" to cause.message))
        }
        exception<BadRequestException> { call, cause ->
            val findPayloadTooLargeException = cause.findPayloadTooLarge()
            if (findPayloadTooLargeException != null) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("message" to findPayloadTooLargeException.message))
            } else {
                call.respond(HttpStatusCode.BadRequest, mapOf("message" to cause.message))
            }
        }
        exception<PayloadTooLargeException> { call, cause ->
            call.respond(HttpStatusCode.PayloadTooLarge, mapOf("message" to cause.message))
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
                HttpMethod.Options -> {
                    handlePreflightRequest(call)
                }

                HttpMethod.Post, HttpMethod.Delete -> {
                    if (isUnauthenticatedApiRoute(path, call.request.httpMethod)) return@onCall
                    call
                        .secureEndpointViolation(requireTlsForPod, clientIpResolver)
                        ?.let { call.respond(it) }
                }

                HttpMethod.Get -> {
                    if (isUnauthenticatedApiRoute(path, call.request.httpMethod)) {
                        return@onCall
                    } else {
                        call
                            .secureEndpointViolation(requireTlsForPod, clientIpResolver)
                            ?.let { call.respond(it) }
                    }
                }

                else -> {
                    call.respond(HttpStatusCode.MethodNotAllowed)
                }
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

private const val API_ROUTE_PREFIX = "/v1/"
private const val APPROVE_GID_ROUTE = "/v1/approvegid"
private const val LEGAL_PRIVACY_ROUTE = "/v1/legal/privacy"
private const val LEGAL_IMPRINT_ROUTE = "/v1/legal/imprint"
private const val LEGAL_TERMS_ROUTE = "/v1/legal/terms"
private const val LEGAL_STATUS_ROUTE = "/v1/legal/status"
private const val ALLOWED_CORS_METHODS = "POST, GET, DELETE, OPTIONS"
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

private fun isUnauthenticatedApiRoute(
    path: String,
    method: HttpMethod,
): Boolean =
    when (method) {
        HttpMethod.Post -> {
            path.equals(APPROVE_GID_ROUTE, ignoreCase = true)
        }

        HttpMethod.Get -> {
            path.equals(LEGAL_PRIVACY_ROUTE, ignoreCase = true) ||
                path.equals(LEGAL_STATUS_ROUTE, ignoreCase = true) ||
                path.startsWith("$LEGAL_PRIVACY_ROUTE/", ignoreCase = true) ||
                path.equals(LEGAL_IMPRINT_ROUTE, ignoreCase = true) ||
                path.startsWith("$LEGAL_IMPRINT_ROUTE/", ignoreCase = true) ||
                path.equals(LEGAL_TERMS_ROUTE, ignoreCase = true) ||
                path.startsWith("$LEGAL_TERMS_ROUTE/", ignoreCase = true)
        }

        else -> {
            false
        }
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
