package straightway.mosa.defense

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.koin.core.Koin
import straightway.mosa.TokenUtil
import straightway.mosa.installNetworkServerPlugins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

class RequestMetricsPluginTest {
    @Test
    fun `records request durations per route`() =
        getKoin().let { koin ->
            testApplication {
                application {
                    installNetworkServerPlugins(
                        koin,
                        requireTlsForPod = false,
                        loadSheddingThreshold = Double.MAX_VALUE,
                        memorySheddingThreshold = Double.MAX_VALUE,
                        loadSheddingRetryAfter = 30.seconds,
                    )
                    routing {
                        get("/hello/{id}") { call.respond("ok") }
                    }
                }

                client.get("/hello/123") {
                    header(HttpHeaders.Authorization, "Bearer ignored")
                }

                val meterRegistry = koin.get<MeterRegistry>()
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

    private fun getKoin() =
        mockk<Koin> {
            every { get<TokenUtil>() } returns NoopTokenUtil
            every { get<MeterRegistry>() } returns SimpleMeterRegistry()
        }

    private object NoopTokenUtil : TokenUtil {
        override fun decode(jwt: String): DecodedJWT {
            error("Unexpected decode invocation")
        }

        override fun isValidMisJwt(jwt: DecodedJWT): Boolean = false
    }
}
