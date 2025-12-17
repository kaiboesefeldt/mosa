package straightway.mosa.defense

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallFailed
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

private val log = KotlinLogging.logger {}

private val StartNsKey = AttributeKey<Long>("RequestMetricsStartNs")
private val FinishedKey = AttributeKey<AtomicBoolean>("RequestMetricsFinished")
val ReqNoKey = AttributeKey<Long>("RequestMetricsReqNo")

val RequestMetricsPlugin =
    createApplicationPlugin(name = "RequestMetricsPlugin") {
        val active = AtomicInteger(0)
        val total = AtomicLong(0)

        fun finish(
            call: ApplicationCall,
            cause: Throwable? = null,
        ) {
            val finished = call.attributes.getOrNull(FinishedKey) ?: return
            if (!finished.compareAndSet(false, true)) return

            val startNs = call.attributes.getOrNull(StartNsKey) ?: return
            val reqNo = call.attributes.getOrNull(ReqNoKey) ?: -1L

            val durationMs = (System.nanoTime() - startNs) / 1_000_000.0
            val activeNow = active.decrementAndGet()

            val route = "${call.request.httpMethod.value} ${call.request.path()}"
            val status =
                call.response
                    .status()
                    ?.value
                    ?.toString() ?: "?"
            val failure = cause?.let { " failed=${it::class.simpleName}" } ?: ""

            log.trace {
                "END req#$reqNo active=$activeNow route=\"$route\" status=$status durationMs=${"%.2f".format(
                    durationMs,
                )}$failure"
            }
        }

        on(CallSetup) { call ->
            call.attributes.put(StartNsKey, System.nanoTime())
            call.attributes.put(FinishedKey, AtomicBoolean(false))

            val activeNow = active.incrementAndGet()
            val reqNo = total.incrementAndGet()
            call.attributes.put(ReqNoKey, reqNo)

            val route = "${call.request.httpMethod.value} ${call.request.path()}"
            log.trace {
                "BEG req#$reqNo active=$activeNow route=\"$route\""
            }
        }

        on(ResponseSent) { call ->
            finish(call, cause = null)
        }

        on(CallFailed) { call, cause ->
            finish(call, cause)
        }
    }
