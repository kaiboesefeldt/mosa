package straightway.mosa.testutils

import io.ktor.server.application.Application
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import straightway.mosa.TokenUtil
import straightway.mosa.defense.requestduration.RequestDurationForRouteStateRegistry
import straightway.mosa.installNetworkServerPlugins
import kotlin.time.Duration.Companion.seconds

fun Application.installTestNetworkServerPlugins(
    requireTlsForPod: Boolean = true,
    tokenUtil: TokenUtil = mockk(),
    meterRegistry: MeterRegistry = mockk(relaxed = true),
) {
    installNetworkServerPlugins(
        requireTlsForPod = requireTlsForPod,
        loadSheddingThreshold = Double.MAX_VALUE,
        memorySheddingThreshold = Double.MAX_VALUE,
        loadSheddingRetryAfter = 30.seconds,
        requestDurationForRouteStateRegistry = RequestDurationForRouteStateRegistry(meterRegistry, mockk(relaxed = true)),
        meterRegistry = meterRegistry,
        nanoClock = mockk(relaxed = true),
        rng = mockk(),
        tokenUtil = tokenUtil,
    )
}
