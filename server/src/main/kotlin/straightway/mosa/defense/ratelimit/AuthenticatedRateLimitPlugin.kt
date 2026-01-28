package straightway.mosa.defense.ratelimit

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.identity.IdentityLogEntryFormatter

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

private val logger = KotlinLogging.logger {}
private const val AUTHENTICATED_RATE_LIMIT_MESSAGE = "Rate limit exceeded"
