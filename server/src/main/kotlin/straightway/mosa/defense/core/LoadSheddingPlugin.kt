package straightway.mosa.defense.core

import com.sun.management.OperatingSystemMXBean
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
import org.koin.core.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.defense.cpu.CpuSheddingDecider
import straightway.mosa.defense.cpu.ProcessCpuTimeSampler
import straightway.mosa.defense.cpu.SmoothedCpuLoadSampler
import straightway.mosa.defense.memory.GenericMemoryUsageSampler
import straightway.mosa.defense.memory.MemorySheddingDecider
import straightway.mosa.defense.memory.SystemMemoryUsageSampler
import straightway.mosa.defense.requestduration.RequestDurationSheddingDecider
import java.lang.management.ManagementFactory
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration

@OptIn(ExperimentalAtomicApi::class)
fun createMosaLoadSheddingPlugin(): Plugin<Application, Unit, Unit> =
    object : Plugin<Application, Unit, Unit> {
        override val key = AttributeKey<Unit>("MosaLoadShedding")

        override fun install(
            pipeline: Application,
            configure: Unit.() -> Unit,
        ) {
            val koin = pipeline.koin()
            val rng = koin.get<Random>()
            val decider = koin.sheddingDeciderChain()
            val retryAfterInfo = koin.get<LoadSheddingConfig>().loadSheddingRetryAfter.toRetryAfterInfo()
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
    if (startShedding.compareAndSet(expectedValue = true, newValue = false)) {
        localLogger.warn { "Stop shedding incoming requests" }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private fun logStartSheddingIfNeeded(
    startShedding: AtomicBoolean,
    result: SheddingDecider.Result,
    retryAfterInfo: RetryAfterInfo,
) {
    if (startShedding.compareAndSet(expectedValue = false, newValue = true)) {
        localLogger.warn {
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

fun Koin.sheddingDeciderChain(): SheddingDecider =
    CombinedSheddingDecider(
        listOfNotNull(
            cpuSheddingDecider(),
            heapMemorySheddingDecider(),
            nonHeapMemorySheddingDecider(),
            systemMemorySheddingDecider(),
            requestDurationSheddingDecider(),
        ),
    )

private fun Koin.requestDurationSheddingDecider() = RequestDurationSheddingDecider(get())

private fun Koin.nonHeapMemorySheddingDecider() =
    with(get<LoadSheddingConfig>()) {
        ManagementFactory.getMemoryMXBean()?.let { memoryBean ->
            MemorySheddingDecider(
                nonHeapMemorySheddingConfig,
                GenericMemoryUsageSampler("NonHeapMemory") { memoryBean.nonHeapMemoryUsage },
            )
        } ?: SheddingDecider { SheddingDecider.Result.NO_SHEDDING }
            .also {
                localLogger.warn { "Memory shedding by non-heap size not available" }
            }
    }

private fun Koin.heapMemorySheddingDecider() =
    with(get<LoadSheddingConfig>()) {
        ManagementFactory.getMemoryMXBean()?.let { memoryBean ->
            MemorySheddingDecider(
                heapMemorySheddingConfig,
                GenericMemoryUsageSampler("NonHeapMemory") { memoryBean.heapMemoryUsage },
            )
        } ?: SheddingDecider { SheddingDecider.Result.NO_SHEDDING }
            .also {
                localLogger.warn { "Memory shedding by heap size not available" }
            }
    }

private fun Koin.systemMemorySheddingDecider() =
    with(get<LoadSheddingConfig>()) {
        getOrNull<OperatingSystemMXBean>()?.let { osBean ->
            MemorySheddingDecider(
                systemMemorySheddingConfig,
                SystemMemoryUsageSampler(osBean),
            )
        } ?: SheddingDecider { SheddingDecider.Result.NO_SHEDDING }
            .also {
                localLogger.warn { "System memory shedding not available" }
            }
    }

private fun Koin.cpuSheddingDecider() =
    with(get<LoadSheddingConfig>()) {
        getOrNull<OperatingSystemMXBean>()?.let { osBean ->
            CpuSheddingDecider(
                cpuSheddingConfig,
                SmoothedCpuLoadSampler(
                    ProcessCpuTimeSampler(
                        nanoClock = get(),
                        minSampleIntervalNs = 1_000_000_000L,
                        numberOfProcessors = Runtime.getRuntime().availableProcessors(),
                        osBean = osBean,
                    ).also {
                        if (!it.isAvailable) {
                            localLogger.warn { "No CPU load shedding: ProcessCpuLoadSampler not available" }
                        }
                    },
                    cpuLoadSmoothingHalfLifeNs,
                    get(),
                ),
            )
        }
    }

private fun SheddingDecider.Result.shouldShedThisRequest(rng: Random) = fullShedding || rng.nextDouble(0.0, 1.0) < sheddingFactor

private val SheddingDecider.Result.noShedding get() = sheddingFactor <= 0.0

private val SheddingDecider.Result.fullShedding get() = 1.0 <= sheddingFactor

private val localLogger = KotlinLogging.logger {}
