package straightway.mosa.defense.core

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

class LoadSheddingConfiguration {
    var decider: SheddingDecider? = null
    var retryAfter: Duration? = null
}

@OptIn(ExperimentalAtomicApi::class)
fun createMosaLoadSheddingPlugin(rng: Random): Plugin<Application, LoadSheddingConfiguration, Unit> =
    object : Plugin<Application, LoadSheddingConfiguration, Unit> {
        override val key = AttributeKey<Unit>("MosaLoadShedding")

        override fun install(
            pipeline: Application,
            configure: LoadSheddingConfiguration.() -> Unit,
        ) {
            val pluginConfig = LoadSheddingConfiguration().apply(configure)
            val decider = pluginConfig.decider
            val retryAfterInfo = pluginConfig.retryAfter.toRetryAfterInfo()
            val startShedding = AtomicBoolean(false)

            pipeline.intercept(ApplicationCallPipeline.Plugins) {
                val didShed =
                    handleLoadSheddingCall(
                        call = call,
                        decider = decider,
                        retryAfterInfo = retryAfterInfo,
                        startShedding = startShedding,
                        rng = rng,
                    )
                if (didShed) finish()
            }
        }
    }

private data class RetryAfterInfo(
    val seconds: String?,
    val messageSuffix: String,
)

private fun Duration?.toRetryAfterInfo() =
    this?.run {
        RetryAfterInfo(
            seconds = max(1, inWholeSeconds).toString(),
            messageSuffix = ", retry after $this",
        )
    } ?: RetryAfterInfo(seconds = null, messageSuffix = "")

private fun resolveOrCacheRouteTag(call: ApplicationCall): String =
    call.attributes.getOrNull(RequestMetricsPlugin.RouteTagKey)
        ?: RouteTagResolver
            .resolveRouteTag(call)
            .also { resolved ->
                runCatching { call.attributes.put(RequestMetricsPlugin.RouteTagKey, resolved) }
            }

private fun toSheddingRequest(call: ApplicationCall): SheddingRequest =
    SheddingRequest(
        method = call.request.httpMethod.value,
        route = resolveOrCacheRouteTag(call),
    )

@OptIn(ExperimentalAtomicApi::class)
private fun stopSheddingIfActive(startShedding: AtomicBoolean) {
    if (startShedding.compareAndSet(true, false)) {
        logger.warn { "Stop shedding incoming requests" }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private fun logStartSheddingIfNeeded(
    startShedding: AtomicBoolean,
    result: SheddingDecider.Result,
    retryAfterInfo: RetryAfterInfo,
) {
    if (startShedding.compareAndSet(false, true)) {
        logger.warn {
            "Start shedding ${result.sheddingFactor} of incoming request: ${result.reason}${retryAfterInfo.messageSuffix}"
        }
    }
}

private suspend fun respondServiceUnavailable(
    call: ApplicationCall,
    retryAfterInfo: RetryAfterInfo,
) {
    retryAfterInfo.seconds?.let { call.response.headers.append(HttpHeaders.RetryAfter, it) }
    call.respond(HttpStatusCode.ServiceUnavailable)
}

@OptIn(ExperimentalAtomicApi::class)
private suspend fun handleLoadSheddingCall(
    call: ApplicationCall,
    decider: SheddingDecider?,
    retryAfterInfo: RetryAfterInfo,
    startShedding: AtomicBoolean,
    rng: Random,
): Boolean {
    if (decider == null) return false

    val result = decider.decideShedding(toSheddingRequest(call))

    if (result.noShedding) {
        stopSheddingIfActive(startShedding)
        return false
    }

    if (!result.shouldShedThisRequest(rng)) return false

    logStartSheddingIfNeeded(startShedding, result, retryAfterInfo)
    respondServiceUnavailable(call, retryAfterInfo)
    return true
}

private fun SheddingDecider.Result.shouldShedThisRequest(rng: Random) = fullShedding || rng.nextDouble(0.0, 1.0) < sheddingFactor

private val SheddingDecider.Result.noShedding get() = sheddingFactor <= 0.0

private val SheddingDecider.Result.fullShedding get() = 1.0 <= sheddingFactor

private val logger = KotlinLogging.logger {}
