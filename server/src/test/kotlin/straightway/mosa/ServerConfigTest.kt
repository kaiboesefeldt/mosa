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
import straightway.mosa.auth.ClientIpResolver
import straightway.mosa.defense.ratelimit.RateLimitConfig
import straightway.mosa.defense.ratelimit.RateLimiter
import straightway.mosa.testutils.installTestNetworkServerPlugins
import kotlin.test.Test
import kotlin.test.assertEquals

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
                installTestNetworkServerPlugins()
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
                installTestNetworkServerPlugins(requireTlsForPod = false)
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

    @Test
    fun `rate limits unauthenticated requests by ip before auth`() =
        testApplication {
            val rateLimiter =
                RateLimiter(
                    { 0L },
                    RateLimitConfig(permitsPerMinute = 1, burst = 1, maxEntries = 10),
                )
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    ipRateLimiter = rateLimiter,
                    clientIpResolver = ClientIpResolver(emptySet()),
                )
                routing {
                    post("/v1/query") { call.respond(HttpStatusCode.OK) }
                }
            }

            val first = client.post("/v1/query")
            val second = client.post("/v1/query")

            assertEquals(HttpStatusCode.Unauthorized, first.status)
            assertEquals(HttpStatusCode.TooManyRequests, second.status)
        }

    @Test
    fun `ip rate limit stops pipeline before route executes`() =
        testApplication {
            val rateLimiter =
                RateLimiter(
                    { 0L },
                    RateLimitConfig(permitsPerMinute = 1, burst = 1, maxEntries = 10),
                )
            var routeCalls = 0
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    ipRateLimiter = rateLimiter,
                    clientIpResolver = ClientIpResolver(emptySet()),
                )
                routing {
                    post("/v1/query") {
                        routeCalls += 1
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val first =
                client.post("/v1/query") {
                    header(HttpHeaders.Authorization, "Bearer test")
                }
            val second =
                client.post("/v1/query") {
                    header(HttpHeaders.Authorization, "Bearer test")
                }

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(HttpStatusCode.TooManyRequests, second.status)
            assertEquals(1, routeCalls)
        }

    private fun ApplicationTestBuilder.configureTestServer(requireTls: Boolean = true) {
        application {
            installTestNetworkServerPlugins(requireTlsForPod = requireTls)
            routing {
                post("/v1/query") { call.respond(HttpStatusCode.OK) }
            }
        }
    }
}
