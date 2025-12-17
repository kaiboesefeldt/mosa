package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.response.respond
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.time.Duration

class LoadSheddingConfiguration {
    var decider: SheddingDecider? = null
    var retryAfter: Duration? = null
}

@OptIn(ExperimentalAtomicApi::class)
fun createMosaLoadSheddingPlugin(): ApplicationPlugin<LoadSheddingConfiguration> =
    createApplicationPlugin("MosaLoadShedding", ::LoadSheddingConfiguration) {
        val decider = pluginConfig.decider
        val (retryAfterSeconds, retryAfterMessage) =
            pluginConfig.retryAfter?.run {
                max(1, inWholeSeconds).toString() to ", retry after $this"
            } ?: (null to "")
        val startShedding = AtomicBoolean(false)

        onCall { call ->
            decider?.shouldShed()?.run {
                if (shouldShed) {
                    if (startShedding.compareAndSet(false, true)) {
                        logger.warn { "Start shedding incoming request: $reason$retryAfterMessage" }
                    }

                    retryAfterSeconds?.let { call.response.headers.append(HttpHeaders.RetryAfter, it) }
                    call.respond(HttpStatusCode.ServiceUnavailable)
                    return@onCall
                } else if (startShedding.compareAndSet(true, false)) {
                    logger.warn { "Stop shedding incoming requests" }
                }
            }
        }
    }

private val logger = KotlinLogging.logger {}
