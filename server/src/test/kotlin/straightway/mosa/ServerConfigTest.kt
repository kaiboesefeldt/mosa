package straightway.mosa

import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
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
        application {
            installNetworkServerPlugins(NoopTokenUtil, requireTls)
            routing {
                post("/v1/query") { call.respond(HttpStatusCode.OK) }
            }
        }
    }

    private object NoopTokenUtil : TokenUtil {
        override fun decode(jwt: String): DecodedJWT {
            error("Unexpected decode invocation")
        }

        override fun isValidMisJwt(jwt: DecodedJWT): Boolean = false
    }
}
