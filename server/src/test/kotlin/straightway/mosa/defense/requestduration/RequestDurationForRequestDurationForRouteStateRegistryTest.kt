package straightway.mosa.defense.requestduration

import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import straightway.mosa.defense.core.RequestMetricsPlugin
import straightway.mosa.defense.core.SheddingRequest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RequestDurationForRequestDurationForRouteStateRegistryTest {
    @Test
    fun `does not create route state when timer is missing`() {
        val registry = SimpleMeterRegistry()
        val factory = mockk<RequestDurationForRouteState.Factory>(relaxed = true)
        val stateRegistry = RequestDurationForRouteStateRegistry(registry, factory)

        val result = stateRegistry.stateFor(SheddingRequest("GET", "/missing"))

        assertNull(result)
        verify(exactly = 0) { factory.create(any()) }
    }

    @Test
    fun `reuses route state for repeated requests`() {
        val registry = SimpleMeterRegistry()
        timerFor(registry, method = "GET", route = "/same")
        val state = mockk<RequestDurationForRouteState>()
        val factory = mockk<RequestDurationForRouteState.Factory>()
        val stateRegistry = RequestDurationForRouteStateRegistry(registry, factory)
        every { factory.create(any()) } returns state

        val first = stateRegistry.stateFor(SheddingRequest("GET", "/same"))
        val second = stateRegistry.stateFor(SheddingRequest("GET", "/same"))

        assertNotNull(first)
        assertNotNull(second)
        verify(exactly = 1) { factory.create(any()) }
    }

    @Suppress("SameParameterValue")
    private fun timerFor(
        meterRegistry: SimpleMeterRegistry,
        method: String,
        route: String,
    ): Timer =
        Timer
            .builder(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME)
            .tag("method", method)
            .tag("route", route)
            .register(meterRegistry)
}
