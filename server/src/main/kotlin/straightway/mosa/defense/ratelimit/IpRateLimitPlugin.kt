package straightway.mosa.defense.ratelimit

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.defense.core.clientIpAddress

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

private const val IP_RATE_LIMIT_MESSAGE = "Rate limit exceeded"
