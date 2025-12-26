package straightway.mosa.defense.core

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import straightway.mosa.testutils.installTestNetworkServerPlugins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RequestMetricsPluginTest {
    @Test
    fun `records request durations per route`() {
        testApplication {
            val meterRegistry = SimpleMeterRegistry()
            application {
                installTestNetworkServerPlugins(meterRegistry = meterRegistry)
                routing {
                    get("/hello/{id}") { call.respond("ok") }
                }
            }

            client.get("/hello/123") {
                header(HttpHeaders.Authorization, "Bearer ignored")
            }

            val timers =
                meterRegistry.find(RequestMetricsPlugin.REQUEST_DURATION_METRIC_NAME).timers()

            assertNotNull(
                timers.firstOrNull(),
                "registered meters: ${meterRegistry.meters.map { it.id.name }.distinct()}",
            )
            assertEquals(
                listOf("/hello/{id}"),
                timers
                    .filter { it.id.getTag("method") == "GET" }
                    .map { it.id.getTag("route") }
                    .distinct(),
            )
            assertEquals(1, timers.sumOf { it.count() })
        }
    }
}
