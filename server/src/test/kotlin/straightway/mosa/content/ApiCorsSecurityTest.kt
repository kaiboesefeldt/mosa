package straightway.mosa.content

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.mockk
import straightway.mosa.testutils.setRawJsonBody
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiCorsSecurityTest {
    private val contentService = mockk<ContentService>(relaxed = true)
    private val environment =
        ControllerHttpTestEnvironment(
            ContentController(
                contentService,
                Clock.fixed(
                    Instant.ofEpochSecond(170000000L),
                    ZoneId.of("UTC"),
                ),
            ),
        ) {
            single<ContentService> { contentService }
        }

    @Test
    fun `responds to preflight with required headers`() =
        environment {
            val response =
                client.request("/v1/publish") {
                    method = HttpMethod.Options
                    preflightHeaders()
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("POST, GET, DELETE, OPTIONS", response.headers[HttpHeaders.AccessControlAllowMethods])
            assertEquals("Authorization, Content-Type", response.headers[HttpHeaders.AccessControlAllowHeaders])
            assertEquals("7200", response.headers[HttpHeaders.AccessControlMaxAge])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
            assertTrue(response.bodyAsText().isEmpty())
        }

    private fun HttpRequestBuilder.preflightHeaders() {
        header(HttpHeaders.Origin, "https://example.test")
        header(HttpHeaders.AccessControlRequestMethod, "POST")
        header(HttpHeaders.AccessControlRequestHeaders, "Authorization")
    }

    @Test
    fun `unauthorized request still exposes cors headers`() =
        environment {
            val response =
                client.request("/v1/publish") {
                    method = HttpMethod.Post
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    header("X-Forwarded-Proto", "https")
                    setRawJsonBody("""{"text":"ignored"}""")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }

    @Test
    fun `rejects insecure api calls`() =
        environment {
            val response =
                client.request("/v1/publish") {
                    method = HttpMethod.Post
                    header(HttpHeaders.XForwardedProto, "http")
                    header(HttpHeaders.Host, "example.com")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(this@ApiCorsSecurityTest.environment.bearerToken)
                    setRawJsonBody("""{"text":"ignored"}""")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }

    @Test
    fun `rejects unsupported api methods`() =
        environment {
            val response =
                client.request("/v1/publish") {
                    method = HttpMethod.Put
                    header("X-Forwarded-Proto", "https")
                    bearerAuth(this@ApiCorsSecurityTest.environment.bearerToken)
                }

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }

    @Test
    fun `accepts secure authorized post request`() =
        environment {
            val response =
                client.request("/v1/publish") {
                    method = HttpMethod.Post
                    header("X-Forwarded-Proto", "https")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    bearerAuth(this@ApiCorsSecurityTest.environment.bearerToken)
                    setRawJsonBody("""{"text":"content"}""")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
}
