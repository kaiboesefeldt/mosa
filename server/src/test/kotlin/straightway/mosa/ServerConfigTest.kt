package straightway.mosa

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.micrometer.core.instrument.MeterRegistry
import io.mockk.mockk
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ServerConfigTest {
    @Test
    fun `rejects http requests without tls`() =
        testApplication {
            configureTestServer()

            val response = client.post("/v1/query")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `rejects http requests for web client when tls required`() =
        testApplication {
            application {
                installNetworkServerPlugins(
                    createTestKoin(),
                    requireTlsForPod = true,
                    loadSheddingThreshold = Double.MAX_VALUE,
                    memorySheddingThreshold = Double.MAX_VALUE,
                    loadSheddingRetryAfter = 30.seconds,
                )
                routing {
                    get("/") { call.respond(HttpStatusCode.OK) }
                }
            }

            val response = client.get("/")

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `allows http requests for web client when tls not required`() =
        testApplication {
            application {
                installNetworkServerPlugins(
                    createTestKoin(),
                    requireTlsForPod = false,
                    loadSheddingThreshold = Double.MAX_VALUE,
                    memorySheddingThreshold = Double.MAX_VALUE,
                    loadSheddingRetryAfter = 30.seconds,
                )
                routing {
                    get("/") { call.respond(HttpStatusCode.OK) }
                }
            }

            val response = client.get("/")

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `rejects http requests for non localhost hosts`() =
        testApplication {
            configureTestServer()

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.Host, "example.com")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `respects forwarded https headers`() =
        testApplication {
            configureTestServer()

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.XForwardedProto, "https")
                    header(HttpHeaders.Host, "example.com")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `allows http requests for non localhost hosts when tls not required`() =
        testApplication {
            configureTestServer(requireTls = false)

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.Host, "example.com")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `allows http requests when tls not required`() =
        testApplication {
            configureTestServer(requireTls = false)

            val response = client.post("/v1/query")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    private fun ApplicationTestBuilder.configureTestServer(requireTls: Boolean = true) {
        val koin = createTestKoin()
        application {
            installNetworkServerPlugins(
                koin,
                requireTlsForPod = requireTls,
                loadSheddingThreshold = Double.MAX_VALUE,
                memorySheddingThreshold = Double.MAX_VALUE,
                loadSheddingRetryAfter = 30.seconds,
            )
            routing {
                post("/v1/query") { call.respond(HttpStatusCode.OK) }
            }
        }
    }

    private fun createTestKoin(): Koin =
        koinApplication {
            modules(
                module {
                    single<MeterRegistry> { mockk<MeterRegistry>(relaxed = true) }
                    single<NanoClock> { NanoClock { 1L } }
                },
            )
        }.koin
}
