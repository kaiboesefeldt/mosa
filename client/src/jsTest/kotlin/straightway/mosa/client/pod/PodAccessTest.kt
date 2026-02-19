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
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.SearchResult
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
        val expectedCertificate =
            PodCertificateResponse(
                certificate = PodCertificate("gid-123", 1234, "signature", "info"),
                refreshRecommendedAt = 1200,
            )
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

        assertRestError("/v1/approvegid", HttpStatusCode.InternalServerError) { podAccess.approveGid("gid-123") }
    }

    @Test
    fun `refreshGid posts without body and returns certificate`() = runTest {
        val expectedCertificate =
            PodCertificateResponse(
                certificate = PodCertificate("gid-123", 1234, "signature", "info"),
                refreshRecommendedAt = 1200,
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedCertificate)

        val certificate = podAccess.refreshGid("jwt-token")

        assertEquals(expectedCertificate, certificate)
        assertRefreshRequest(
            capturedRequests.single(),
            route = "v1/refreshgid",
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `refreshGid throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/refreshgid", HttpStatusCode.BadRequest) { podAccess.refreshGid("jwt-token") }
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

        assertRestError("/v1/publish", HttpStatusCode.BadRequest) {
            podAccess.publish(PublishRequest("message"), "jwt-token")
        }
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

        assertRestError("/v1/reply", HttpStatusCode.BadRequest) {
            podAccess.reply(ReplyRequest(PublishRequest("message"), "root-id"), "jwt-token")
        }
    }

    @Test
    fun `deletePosts sends delete and returns result`() = runTest {
        val expectedResult = DeletePostsResult(listOf("id-1", "id-2"))
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)

        val result = podAccess.deletePosts("jwt-token")

        assertEquals(expectedResult, result)
        assertDeleteRequest(
            capturedRequests.single(),
            route = "v1/posts",
            bearerToken = "jwt-token",
        )
    }

    @Test
    fun `deletePosts throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/posts", HttpStatusCode.BadRequest) { podAccess.deletePosts("jwt-token") }
    }

    @Test
    fun `deletePost sends delete and returns result`() = runTest {
        val expectedResult = DeletePostsResult(listOf("id-1"))
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)

        val result = podAccess.deletePost("id-1", "jwt-token")

        assertEquals(expectedResult, result)
        assertDeleteRequest(
            capturedRequests.single(),
            route = "v1/posts/id-1",
            bearerToken = "jwt-token",
        )
    }

    @Test
    fun `deletePost throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/posts/id-1", HttpStatusCode.BadRequest) { podAccess.deletePost("id-1", "jwt-token") }
    }

    @Test
    fun `search posts request and returns result`() = runTest {
        val expectedResult =
            SearchResult(
                entries =
                    listOf(
                        SearchResult.Entry("id", 0.9f),
                    ),
                queryOnId = "query-on",
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)
        val queryRequest = QueryRequest(topic = "topic", fromTime = 12, threadRootId = "root")

        val result = podAccess.search(queryRequest, "jwt-token")

        assertEquals(expectedResult, result)

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/search",
            expectedPayload = queryRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `search throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/search", HttpStatusCode.BadRequest) { podAccess.search(QueryRequest(), "jwt-token") }
    }

    @Test
    fun `searchOn posts request and returns result`() = runTest {
        val expectedResult =
            SearchResult(
                entries =
                    listOf(
                        SearchResult.Entry("id", 0.4f),
                    ),
                queryOnId = "another-id",
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)
        val queryOnRequest = QueryOnRequest("query-on-id")

        val result = podAccess.searchOn(queryOnRequest, "jwt-token")

        assertEquals(expectedResult, result)

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/searchon",
            expectedPayload = queryOnRequest,
            bearerToken = "jwt-token"
        )
    }

    @Test
    fun `searchOn throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/searchon", HttpStatusCode.BadRequest) { podAccess.searchOn(QueryOnRequest("id"), "jwt-token") }
    }

    @Test
    fun `fetch posts request and returns result`() = runTest {
        val expectedResult =
            FetchResult(
                entries =
                    listOf(
                        FetchResult.Entry("id", "content", "author", 42),
                    ),
            )
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, expectedResult)
        val fetchRequest = FetchRequest(listOf("id"))

        val result = podAccess.fetch(fetchRequest, "jwt-token")

        assertEquals(expectedResult, result)

        assertRequest(
            capturedRequests.single(),
            HttpMethod.Post,
            route = "v1/fetch",
            expectedPayload = fetchRequest,
            bearerToken = "jwt-token",
        )
    }

    @Test
    fun `privacy baseline clear terms sends get and returns raw json`() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, "{\"kind\":\"baseline\"}")

        val result = podAccess.privacyBaselineClearTerms()

        assertEquals("{\"kind\":\"baseline\"}", result)
        assertGetRequest(capturedRequests.single(), "v1/legal/privacy/baseline")
    }

    @Test
    fun `privacy default clear terms sends get and returns raw json`() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, "{\"kind\":\"profile\",\"id\":\"default\"}")

        val result = podAccess.privacyDefaultClearTerms()

        assertEquals("{\"kind\":\"profile\",\"id\":\"default\"}", result)
        assertGetRequest(capturedRequests.single(), "v1/legal/privacy/default")
    }

    @Test
    fun `privacy mosa clear terms sends get and returns raw json`() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, "{\"kind\":\"profile\",\"id\":\"mosa\"}")

        val result = podAccess.privacyMosaClearTerms()

        assertEquals("{\"kind\":\"profile\",\"id\":\"mosa\"}", result)
        assertGetRequest(capturedRequests.single(), "v1/legal/privacy/mosa")
    }

    @Test
    fun `privacy operator clear terms sends get and returns raw json`() = runTest {
        val capturedRequests = mutableListOf<HttpRequestData>()
        val podAccess = podAccessResponding(HttpStatusCode.OK, capturedRequests, "{\"kind\":\"profile\",\"id\":\"operator\"}")

        val result = podAccess.privacyOperatorClearTerms()

        assertEquals("{\"kind\":\"profile\",\"id\":\"operator\"}", result)
        assertGetRequest(capturedRequests.single(), "v1/legal/privacy/operator")
    }

    @Test
    fun `privacy baseline clear terms throws when backend returns error`() = runTest {
        val podAccess = podAccessResponding(HttpStatusCode.BadRequest)

        assertRestError("/v1/legal/privacy/baseline", HttpStatusCode.BadRequest) { podAccess.privacyBaselineClearTerms() }
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

    private fun podAccessResponding(
        status: HttpStatusCode,
        capturedRequests: MutableList<HttpRequestData>,
        payload: String,
    ): PodAccess =
        podAccess { request ->
            capturedRequests += request
            respond(
                content = payload,
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

    private fun assertRefreshRequest(
        request: HttpRequestData,
        route: String,
        bearerToken: String,
    ) {
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://pod.example/$route", request.url.toString())
        assertEquals("Bearer $bearerToken", request.headers[HttpHeaders.Authorization])
    }

    private fun assertDeleteRequest(
        request: HttpRequestData,
        route: String,
        bearerToken: String,
    ) {
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://pod.example/$route", request.url.toString())
        assertEquals("Bearer $bearerToken", request.headers[HttpHeaders.Authorization])
    }

    private fun assertGetRequest(
        request: HttpRequestData,
        route: String,
    ) {
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://pod.example/$route", request.url.toString())
        assertNull(request.headers[HttpHeaders.Authorization])
    }

    private suspend fun assertRestError(path: String, status: HttpStatusCode, call: suspend () -> Unit) {
        val error = assertFailsWith<PodRequestFailed> { call() }
        assertEquals(status, error.status)
        assertTrue(error.path.contains(path))
    }
}
