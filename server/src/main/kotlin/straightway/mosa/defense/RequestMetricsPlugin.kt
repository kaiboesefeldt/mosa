package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.path
import io.ktor.server.routing.routingRoot
import io.ktor.util.AttributeKey
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RequestMetricsPlugin(
    private val meterRegistry: MeterRegistry,
    private val nanoTime: () -> Long = System::nanoTime,
) : Plugin<Application, Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("RequestMetricsPlugin")

    override fun install(
        pipeline: Application,
        configure: (Unit) -> Unit,
    ) {
        configure(Unit)

        val active = AtomicInteger(0)
        val total = AtomicLong(0)

        pipeline.intercept(ApplicationCallPipeline.Setup) {
            val startNs = nanoTime()
            val activeNow = active.incrementAndGet()
            val reqNo = total.incrementAndGet()
            call.attributes.put(ReqNoKey, reqNo)

            val route = "${call.request.httpMethod.value} ${call.request.path()}"

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
                    val durationNs = nanoTime() - startNs
                    recordDuration(call, durationNs)
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

    private fun recordDuration(
        call: io.ktor.server.application.ApplicationCall,
        durationNs: Long,
    ) {
        val route = resolveRouteTag(call)
        val method = call.request.httpMethod.value

        Timer
            .builder(REQUEST_DURATION_METRIC_NAME)
            .tag("method", method)
            .tag("route", route)
            .register(meterRegistry)
            .record(durationNs, TimeUnit.NANOSECONDS)
    }

    private fun resolveRouteTag(call: io.ktor.server.application.ApplicationCall): String =
        resolveRoutingPath(call)?.takeIf { it.isNotBlank() } ?: "UNMATCHED"

    private fun resolveRoutingPath(call: io.ktor.server.application.ApplicationCall): String? {
        val requestPath = call.request.path()
        val candidates = collectRoutePaths(call.application.routingRoot)
        return candidates
            .mapNotNull { routePath -> scoreRouteMatch(routePath, requestPath)?.let { score -> routePath to score } }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun collectRoutePaths(root: io.ktor.server.routing.RoutingNode): List<String> {
        val paths = mutableListOf<String>()
        val nodes = ArrayDeque<io.ktor.server.routing.RoutingNode>()
        nodes.add(root)
        while (nodes.isNotEmpty()) {
            val node = nodes.removeFirst()
            val routePath = node.path
            if (routePath.isNotBlank() && routePath != "/") {
                paths.add(routePath)
            }
            nodes.addAll(node.children)
        }
        return paths
    }

    private fun scoreRouteMatch(
        routePath: String,
        requestPath: String,
    ): Int? {
        val routeSegments = routePath.trim('/').split('/').filter { it.isNotEmpty() }
        val requestSegments = requestPath.trim('/').split('/').filter { it.isNotEmpty() }
        if (routeSegments.size != requestSegments.size) return null

        var score = 0
        for (index in routeSegments.indices) {
            val routeSegment = routeSegments[index]
            val requestSegment = requestSegments[index]
            if (isPathParameter(routeSegment)) {
                score += 1
            } else {
                if (routeSegment != requestSegment) return null
                score += 2
            }
        }
        return score
    }

    private fun isPathParameter(segment: String): Boolean = segment.startsWith("{") && segment.endsWith("}") && segment.length > 2

    companion object {
        const val REQUEST_DURATION_METRIC_NAME = "mosa.http.server.request.duration"

        private val ReqNoKey = AttributeKey<Long>("RequestMetricsReqNo")

        private val logger = KotlinLogging.logger {}
    }
}
