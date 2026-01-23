package straightway.mosa.testutils

import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import straightway.mosa.TokenUtil
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.ClientBlockList
import straightway.mosa.defense.cpu.CpuSheddingConfig
import straightway.mosa.defense.memory.MemorySheddingConfig
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.defense.requestguard.RequestBodyLimitConfig
import straightway.mosa.defense.requestguard.defaultMaxRequestBodyBytes
import straightway.mosa.installNetworkServerPlugins
import java.nio.file.Path
import java.time.Clock
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
    requestBodyLimitConfig: RequestBodyLimitConfig =
        RequestBodyLimitConfig(
            maxBodyBytes = defaultMaxRequestBodyBytes(maxFetchItems = 100),
        ),
    ipBlockList: AutoRefreshingFileBackedContent<ClientBlockList> = defaultIpBlockList(),
) {
    installNetworkServerPlugins(
        requireTlsForPod = requireTlsForPod,
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
        requestDurationForRouteStateRegistry = RequestDurationForRouteStateRegistry(meterRegistry, mockk(relaxed = true)),
        meterRegistry = meterRegistry,
        nanoClock = mockk(relaxed = true),
        rng = mockk(),
        tokenUtil = tokenUtil,
        ipRateLimiter = ipRateLimiter,
        clientIpResolver = clientIpResolver,
        requestBodyLimitConfig = requestBodyLimitConfig,
        ipBlockList = ipBlockList,
    )
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
