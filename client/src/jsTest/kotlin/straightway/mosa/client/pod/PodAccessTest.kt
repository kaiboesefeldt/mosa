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
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.ReplyRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PodAccessTest {
    private val json = Json { encodeDefaults = true }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    @Test
    fun `approveGid posts gid and returns certificate`() = runTest {
        val expectedCertificate = PodCertificate("gid-123", 1234, "signature", "info")
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedCertificate)

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
        val podAccess = podAccessResponding(HttpStatusCode.InternalServerError)

        assertRestError("/v1/approvegid") { podAccess.approveGid("gid-123") }
    }

    @Test
    fun `publish posts content and accepts success`() = runTest {
        val publishRequest = PublishRequest("message")
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.Created, capturedRequests)

        podAccess.publish(publishRequest, "jwt-token")

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/publish",
            expectedPayload = publishRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `publish throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/publish") { podAccess.publish(PublishRequest("message"), "jwt-token") }
    }

    @Test
    fun `reply posts content and accepts success`() = runTest {
        val replyRequest = ReplyRequest(PublishRequest("message"), "root-id")
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.Created, capturedRequests)

        podAccess.reply(replyRequest, "jwt-token")

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/reply",
            expectedPayload = replyRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `reply throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/reply") {
            podAccess.reply(ReplyRequest(PublishRequest("message"), "root-id"), "jwt-token")
        }
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
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)
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
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/query") { podAccess.query(QueryRequest(), "jwt-token") }
    }

    @Test
    fun `queryOn posts request and returns result`() = runTest {
        val expectedResult =
            QueryResult(
                entries =
                    listOf(
                        QueryResult.Entry("id", "content", 0.4f, "author", 21, "root"),
                    ),
                queryOnId = "another-id",
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)
        val queryOnRequest = QueryOnRequest("query-on-id")

        val result = podAccess.queryOn(queryOnRequest, "jwt-token")

        assertEquals(expectedResult, result)

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/queryon",
            expectedPayload = queryOnRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `queryOn throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/queryon") { podAccess.queryOn(QueryOnRequest("id"), "jwt-token") }
    }

    private fun podAccess(handler: MockRequestHandler): PodAccess {
        val client =
            HttpClient(MockEngine) {
                install(ContentNegotiation) { json() }
                engine {
                    addHandler(handler)
                }
            }
        return PodAccessImpl("https://pod.example", client)
    }

    private fun podAccessResponding(
        status: HttpStatusCode,
        capturedRequests: MutableList<HttpRequestData> = mutableListOf(),
    ): PodAccess =
        podAccess { request ->
            capturedRequests += request
            respond(
                content = "",
                status = status,
                headers = jsonHeaders,
            )
        }

    private inline fun <reified T> podAccessResponding(
        status: HttpStatusCode,
        capturedRequests: MutableList<HttpRequestData>,
        payload: T,
    ): PodAccess =
        podAccess { request ->
            capturedRequests += request
            respond(
                content = json.encodeToString(payload),
                status = status,
                headers = jsonHeaders,
            )
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

    private suspend fun assertRestError(path: String, call: suspend () -> Unit) {
        val error = assertFailsWith<RuntimeException> { call() }
        assertTrue(error.message!!.contains(path))
    }
}
