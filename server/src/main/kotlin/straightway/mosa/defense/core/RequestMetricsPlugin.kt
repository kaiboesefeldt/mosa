package straightway.mosa.defense.core

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.koin.ktor.plugin.koin
import straightway.mosa.NanoClock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RequestMetricsPlugin : Plugin<Application, Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("RequestMetricsPlugin")

    private val timers = ConcurrentHashMap<RequestMetricKey, Timer>()

    override fun install(
        pipeline: Application,
        configure: (Unit) -> Unit,
    ) {
        configure(Unit)

        val koin = pipeline.koin()
        val meterRegistry = koin.get<MeterRegistry>()
        val nanoClock = koin.get<NanoClock>()

        val active = AtomicInteger(0)
        val total = AtomicLong(0)

        pipeline.intercept(ApplicationCallPipeline.Setup) {
            val startNs = nanoClock.nanoTime()
            val activeNow = active.incrementAndGet()
            val reqNo = total.incrementAndGet()
            call.attributes.put(ReqNoKey, reqNo)

            val route = "${call.request.httpMethod.value} ${call.request.path()}"
            call.attributes.put(RouteTagKey, RouteTagResolver.resolveRouteTag(call))

            var failure: Throwable? = null
            withContext(MDCContext(mapOf("reqNo" to "req#$reqNo"))) {
                logger.trace {
                    "START($activeNow) $route"
                }
                try {
                    proceed()
                } catch (t: Throwable) {
                    failure = t
                    throw t
                } finally {
                    val durationNs = nanoClock.nanoTime() - startNs
                    meterRegistry.recordDuration(call, durationNs)
                    val activeAfter = active.decrementAndGet()

                    val status = call.response.status() ?: "<UNKNOWN STATUS>"
                    val baseMsg = "($activeAfter) $route $status"
                    val duration = "${"%.2f".format(durationNs / 1_000_000.0)} ms"

                    if (failure == null) {
                        logger.trace { "END$baseMsg $duration" }
                    } else {
                        logger.warn(failure) { "FAILED$baseMsg ${failure::class.simpleName} $duration" }
                    }
                }
            }
        }
    }

    private fun MeterRegistry.recordDuration(
        call: ApplicationCall,
        durationNs: Long,
    ) {
        val route = call.attributes.getOrNull(RouteTagKey) ?: RouteTagResolver.resolveRouteTag(call)
        val method = call.request.httpMethod.value

        timers
            .computeIfAbsent(RequestMetricKey(method = method, route = route)) {
                Timer
                    .builder(REQUEST_DURATION_METRIC_NAME)
                    .tag("method", method)
                    .tag("route", route)
                    .register(this)
            }.record(durationNs, TimeUnit.NANOSECONDS)
    }

    private data class RequestMetricKey(
        val method: String,
        val route: String,
    )

    companion object {
        const val REQUEST_DURATION_METRIC_NAME = "mosa.http.server.request.duration"

        private val ReqNoKey = AttributeKey<Long>("RequestMetricsReqNo")
        val RouteTagKey = AttributeKey<String>("RequestMetricsRouteTag")

        private val logger = KotlinLogging.logger {}
    }
}
