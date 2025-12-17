package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.Plugin
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class RequestMetricsPlugin : Plugin<Application, Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("RequestMetricsPlugin")

    override fun install(
        pipeline: Application,
        configure: (Unit) -> Unit,
    ) {
        configure(Unit)

        val active = AtomicInteger(0)
        val total = AtomicLong(0)

        pipeline.intercept(ApplicationCallPipeline.Setup) {
            call.attributes.put(StartNsKey, System.nanoTime())

            val activeNow = active.incrementAndGet()
            val reqNo = total.incrementAndGet()
            call.attributes.put(ReqNoKey, reqNo)

            val route = "${call.request.httpMethod.value} ${call.request.path()}"

            var failure: Throwable? = null
            withContext(MDCContext(mapOf("reqNo" to "req#$reqNo"))) {
                log.trace {
                    "START($activeNow) $route"
                }
                try {
                    proceed()
                } catch (t: Throwable) {
                    failure = t
                    throw t
                } finally {
                    call.attributes.getOrNull(StartNsKey)?.let { startNs ->
                        val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
                        val activeAfter = active.decrementAndGet()

                        val status =
                            call.response
                                .status()
                                ?.value
                                ?.toString() ?: "?"
                        val failureDescription =
                            failure?.let { " failed=${it::class.simpleName}" } ?: ""

                        log.trace {
                            "END($activeAfter) $route $status ${
                                "%.2f".format(
                                    durationMs,
                                )
                            } ms$failureDescription"
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val ReqNoKey = AttributeKey<Long>("RequestMetricsReqNo")
        private val StartNsKey = AttributeKey<Long>("RequestMetricsStartNs")

        private val log = KotlinLogging.logger {}
    }
}
