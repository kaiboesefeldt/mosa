package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpStatusCode
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

class RequestMetricsPlugin(
    private val interceptor: Interceptor? = null,
    private val nanoTime: () -> Long = System::nanoTime,
) : Plugin<Application, Unit, Unit> {
    fun interface Interceptor {
        suspend fun intercept(proceedAndReturnDurationMs: suspend () -> Double?): HttpStatusCode?
    }

    override val key: AttributeKey<Unit> = AttributeKey("RequestMetricsPlugin")

    override fun install(
        pipeline: Application,
        configure: (Unit) -> Unit,
    ) {
        configure(Unit)

        val active = AtomicInteger(0)
        val total = AtomicLong(0)

        pipeline.intercept(ApplicationCallPipeline.Setup) {
            call.attributes.put(StartNsKey, nanoTime())

            val activeNow = active.incrementAndGet()
            val reqNo = total.incrementAndGet()
            call.attributes.put(ReqNoKey, reqNo)

            val route = "${call.request.httpMethod.value} ${call.request.path()}"

            var failure: Throwable? = null
            withContext(MDCContext(mapOf("reqNo" to "req#$reqNo"))) {
                logger.trace {
                    "START($activeNow) $route"
                }
                var durationMs: Double = Double.NaN
                try {
                    doIntercept {
                        proceed()
                        call.attributes
                            .getOrNull(StartNsKey)
                            ?.let { (nanoTime() - it) / 1_000_000.0 }
                            ?.also { durationMs = it }
                            ?: Double.NaN
                    }?.let { call.response.status(it) }
                } catch (t: Throwable) {
                    failure = t
                    call.response.status(HttpStatusCode.InternalServerError)
                    logger.warn(t) { "Exception while handling $route" }
                } finally {
                    val activeAfter = active.decrementAndGet()

                    val status = call.response.status() ?: "<UNKNOWN STATUS>"
                    val baseMsg = "($activeAfter) $route $status"
                    val duration = "${"%.2f".format(durationMs)} ms"

                    if (failure == null) {
                        logger.trace { "END$baseMsg $duration" }
                    } else {
                        logger.warn { "FAILED$baseMsg ${failure::class.simpleName} $duration" }
                    }
                }
            }
        }
    }

    private suspend fun doIntercept(proceedAndReturnDurationMs: suspend () -> Double?): HttpStatusCode? =
        if (interceptor == null) {
            proceedAndReturnDurationMs()
            null
        } else {
            interceptor.intercept(proceedAndReturnDurationMs)
        }

    companion object {
        private val ReqNoKey = AttributeKey<Long>("RequestMetricsReqNo")
        private val StartNsKey = AttributeKey<Long>("RequestMetricsStartNs")

        private val logger = KotlinLogging.logger {}
    }
}
