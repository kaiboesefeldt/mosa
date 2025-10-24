package straightway.mosa.client.pod

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodAccessTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `approveGid posts gid and returns certificate`() = runTest {
        val expectedCertificate = PodCertificate("gid-123", 1234, "signature", "info")
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess =
            podAccess { request ->
                capturedRequests += request
                respond(
                    content = json.encodeToString(expectedCertificate),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val certificate = podAccess.approveGid("gid-123")

        assertEquals(expectedCertificate, certificate)
        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            "v1/approvegid",
            AuthenticationApprovalRequest("gid-123")
        )
    }

    @Test
    fun `approveGid throws when backend returns error`() = runTest {
        val podAccess =
            podAccess {
                respond(
                    content = "",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }

        val error = assertFailsWith<RuntimeException> { podAccess.approveGid("gid-123") }
        assertTrue(error.message!!.contains("/v1/approvegid"))
    }

    @Test
    fun `query posts request and returns result`() = runTest {
        val expectedResult =
            QueryResult(
                entries =
                    listOf(
                        QueryResult.Entry("id", "content", 0.9f, "author", 42, "root"),
                    ),
                queryOnId = "query-on",
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess =
            podAccess { request ->
                capturedRequests += request
                respond(
                    content = json.encodeToString(expectedResult),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val queryRequest = QueryRequest(topic = "topic", minTime = 12, threadRootId = "root")

        val result = podAccess.query(queryRequest, "jwt-token")

        assertEquals(expectedResult, result)

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/query",
            expectedPayload = queryRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `query throws when backend returns error`() = runTest {
        val podAccess =
            podAccess {
                respond(
                    content = "",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val error = assertFailsWith<RuntimeException> { podAccess.query(QueryRequest(), "jwt-token") }
        assertTrue(error.message!!.contains("/v1/query"))
    }

    private fun podAccess(handler: MockRequestHandler): PodAccess {
        val client =
            HttpClient(MockEngine) {
                install(ContentNegotiation) { json() }
                engine {
                    addHandler(handler)
                }
            }
        return PodAccess("https://pod.example", client)
    }

    private inline fun <reified T> assertRequest(
        request: HttpRequestData,
        method: HttpMethod,
        route: String,
        expectedPayload: T,
        bearerToken: String? = null,
    ) {
        assertEquals(method, request.method)
        assertEquals("https://pod.example/$route", request.url.toString())
        if (bearerToken != null)
            assertEquals("Bearer $bearerToken", request.headers[HttpHeaders.Authorization])
        else assertNull(request.headers[HttpHeaders.Authorization])

        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType)

        val payload = json.decodeFromString<T>(body.text)
        assertEquals(expectedPayload, payload)
    }
}
