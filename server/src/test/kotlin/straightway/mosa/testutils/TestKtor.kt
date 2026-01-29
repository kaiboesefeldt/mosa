package straightway.mosa.testutils

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.pluginOrNull
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import straightway.mosa.NanoClock
import straightway.mosa.TlsConfig
import straightway.mosa.TokenUtil
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.defense.core.LoadSheddingConfig
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.ipRateLimit
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.defaultMaxRequestBodyBytes
import straightway.mosa.installNetworkServerPlugins
import straightway.mosa.network.ClientIpResolver
import java.nio.file.Path
import java.time.Clock
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

fun Application.installTestNetworkServerPlugins(
    requireTlsForPod: Boolean = true,
    tokenUtil: TokenUtil = mockk(),
    meterRegistry: MeterRegistry = mockk(relaxed = true),
    ipRateLimiter: RateLimiter =
        RateLimiter(
            { 0L },
            RateLimitConfig(permitsPerMinute = 10_000, burst = 10_000),
        ),
    clientIpResolver: ClientIpResolver = ClientIpResolver(emptySet()),
    ipBlockList: AutoRefreshingFileBackedContent<ClientBlockList> = defaultIpBlockList(),
    requestBodyLimitConfig: RequestBodyLimitConfig =
        RequestBodyLimitConfig(
            maxBodyBytes = defaultMaxRequestBodyBytes(maxFetchItems = 100),
        ),
    loadSheddingConfig: LoadSheddingConfig =
        LoadSheddingConfig(
            cpuSheddingConfig =
                CpuSheddingConfig(
                    loadSheddingStart = Double.MAX_VALUE,
                    loadSheddingFull = Double.MAX_VALUE,
                ),
            heapMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            nonHeapMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            systemMemorySheddingConfig =
                MemorySheddingConfig(
                    memorySheddingStart = Double.MAX_VALUE,
                    memorySheddingFull = Double.MAX_VALUE,
                ),
            cpuLoadSmoothingHalfLifeNs = 250_000_000L,
            loadSheddingRetryAfter = 30.seconds,
        ),
) {
    val nanoClock = mockk<NanoClock>(relaxed = true)
    if (pluginOrNull(Koin) == null) {
        install(Koin) {
            allowOverride(true)
        }
    }
    val koin = koin()
    val needsMeterRegistry = koin.getOrNull<MeterRegistry>() == null
    val needsNanoClock = koin.getOrNull<NanoClock>() == null
    val needsRandom = koin.getOrNull<Random>() == null
    val needsRequestDurationRegistry = koin.getOrNull<RequestDurationForRouteStateRegistry>() == null
    koin.loadModules(
        listOf(
            module {
                if (needsMeterRegistry) {
                    single<MeterRegistry> { meterRegistry }
                }
                if (needsNanoClock) {
                    single<NanoClock> { nanoClock }
                }
                if (needsRandom) {
                    single<Random> { Random(get<NanoClock>().nanoTime()) }
                }
                if (needsRequestDurationRegistry) {
                    single { RequestDurationForRouteStateRegistry(get(), mockk(relaxed = true)) }
                }
                single { TlsConfig(requireTlsForPod = requireTlsForPod) }
                single { loadSheddingConfig }
                single { tokenUtil }
                single { clientIpResolver }
                single {
                    requestBodyLimitConfig
                }
                single(ipRateLimit) {
                    ipRateLimiter
                }
                single(straightway.mosa.defense.ipBlockList) {
                    ipBlockList
                }
            },
        ),
        createEagerInstances = false,
    )
    installNetworkServerPlugins()
}

private fun defaultIpBlockList(): AutoRefreshingFileBackedContent<ClientBlockList> =
    AutoRefreshingFileBackedContent(
        AutoRefreshingFileBackedContent.Config(
            path = Path.of("mosa_ip_blocklist_test.yaml"),
            refreshInterval = 5.seconds,
        ),
        Clock.systemUTC(),
    ) { content ->
        ClientBlockList.transformer.transform(content)
    }
