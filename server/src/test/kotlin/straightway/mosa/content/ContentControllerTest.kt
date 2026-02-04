package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authentication
import io.ktor.server.auth.bearer
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.base.register
import straightway.mosa.testutils.setJsonBody
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

class ContentControllerTest {
    private val test get() =
        object {
            val contentService =
                mockk<ContentService>()
            val env =
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
        }

    @Nested
    inner class PublishEndpoint : TestEndpoint("/v1/publish") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) {
                        it.contentService.publish(any(), any(), any())
                    }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                env {
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                        )
                    coEvery {
                        contentService.publish(env.gid, request, any())
                    } returns ""

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) {
                        contentService.publish(env.gid, request, any())
                    }
                }
            }

        @Test
        fun `should return 401 Unauthorized when principal disappears`() =
            with(test) {
                env {
                    application {
                        install(
                            createApplicationPlugin("DropPrincipal") {
                                onCall { call ->
                                    call.authentication.principal(
                                        Any(),
                                    )
                                }
                            },
                        )
                    }

                    val request =
                        PublishRequest(
                            text = "Text to publish",
                        )

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any())
                    }
                }
            }

        @Test
        fun `should return 401 Unauthorized when principal gid access fails without message`() =
            testApplication {
                val contentService = mockk<ContentService>()
                val principal = mockk<PodPrincipal>()
                every { principal.gid } throws RuntimeException()

                application {
                    install(Authentication) {
                        bearer {
                            authenticate { principal }
                        }
                    }
                    install(ServerContentNegotiation) {
                        jackson()
                    }
                    routing {
                        register(
                            ContentController(
                                contentService,
                                Clock.fixed(
                                    Instant.ofEpochSecond(170000000L),
                                    ZoneId.of("UTC"),
                                ),
                            ),
                        )
                    }
                }

                client =
                    createClient {
                        install(ClientContentNegotiation) {
                            jackson()
                        }
                    }

                val response =
                    client.post("/v1/publish") {
                        setJsonBody(
                            PublishRequest(
                                text = "Text to publish",
                            ),
                        )
                        bearerAuth("token")
                    }

                assertEquals(HttpStatusCode.Unauthorized, response.status)
                assertEquals(mapOf("message" to "Missing authenticated principal"), response.body())
                coVerify(exactly = 0) {
                    contentService.publish(any(), any(), any())
                }
            }
    }

    @Nested
    inner class ReplyEndpoint : TestEndpoint("/v1/reply") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.reply(any(), any(), any()) }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                env {
                    val request =
                        ReplyRequest(
                            content =
                                PublishRequest(
                                    text = "Text to publish",
                                ),
                            rootId = "01234567",
                        )
                    coEvery { contentService.reply(env.gid, request, any()) } returns ""

                    val response =
                        client.post("/v1/reply") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.reply(env.gid, request, any()) }
                }
            }
    }

    @Nested
    inner class DeleteEndpoint {
        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val deletedIds = listOf("post-1", "reply-1")
                    coEvery { contentService.delete(env.gid, "post-1") } returns deletedIds

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(DeletePostsResult(deletedIds), response.body())
                    coVerify(exactly = 1) { contentService.delete(env.gid, "post-1") }
                }
            }

        @Test
        fun `should return 404 Not Found when post is missing`() =
            with(test) {
                env {
                    coEvery { contentService.delete(env.gid, "post-1") } throws
                        ContentService.ContentNotFoundException()

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
            }

        @Test
        fun `should return 403 Forbidden when post belongs to another user`() =
            with(test) {
                env {
                    coEvery { contentService.delete(env.gid, "post-1") } throws
                        ContentService.ContentForbiddenException()

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                env {
                    val response = client.delete("/v1/posts/post-1")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.delete(any(), any()) }
                }
            }
    }

    @Nested
    inner class DeleteAllEndpoint {
        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val deletedIds = listOf("post-1", "post-2")
                    coEvery { contentService.deleteAllForUser(env.gid) } returns deletedIds

                    val response =
                        client.delete("/v1/posts") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(DeletePostsResult(deletedIds), response.body())
                    coVerify(exactly = 1) { contentService.deleteAllForUser(env.gid) }
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                env {
                    val response = client.delete("/v1/posts")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.deleteAllForUser(any()) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint : TestEndpoint("/v1/query") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.query(any<QueryRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryRequest(
                            topic = "Hello World",
                            fromTime = 1L,
                            threadRootId = "12345",
                        )
                    coEvery { contentService.query(any<QueryRequest>()) } returns queryResult

                    val response =
                        client.post("/v1/query") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.query(request) }
                    assertEquals(queryResult, response.body<QueryResult>())
                }
            }
    }

    @Nested
    inner class QueryOnEndpoint : TestEndpoint("/v1/queryon") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.queryOn(any<QueryOnRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryOnRequest(queryOnId = "queryOnId")
                    coEvery { contentService.queryOn(any<QueryOnRequest>()) } returns queryResult

                    val response =
                        client.post("/v1/queryon") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.queryOn(request) }
                    assertEquals(queryResult, response.body<QueryResult>())
                }
            }
    }

    @Nested
    inner class SearchEndpoint : TestEndpoint("/v1/search") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.search(any<QueryRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryRequest(
                            topic = "Hello World",
                            fromTime = 1L,
                            threadRootId = "12345",
                        )
                    coEvery { contentService.search(any<QueryRequest>()) } returns searchResult

                    val response =
                        client.post("/v1/search") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.search(request) }
                    assertEquals(searchResult, response.body<SearchResult>())
                }
            }
    }

    @Nested
    inner class SearchOnEndpoint : TestEndpoint("/v1/searchon") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.searchOn(any<QueryOnRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryOnRequest(queryOnId = "queryOnId")
                    coEvery { contentService.searchOn(any<QueryOnRequest>()) } returns searchResult

                    val response =
                        client.post("/v1/searchon") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.searchOn(request) }
                    assertEquals(searchResult, response.body<SearchResult>())
                }
            }
    }

    @Nested
    inner class FetchEndpoint : TestEndpoint("/v1/fetch") {
        private val fetchResult =
            FetchResult(
                entries =
                    listOf(
                        FetchResult.Entry(
                            id = "id1",
                            content = "content1",
                            authorGID = "authorGID1",
                            storedAt = 12345,
                            rootId = "rootId1",
                        ),
                        FetchResult.Entry(
                            id = "id2",
                            content = "content2",
                            authorGID = "authorGID2",
                            storedAt = 23456,
                            rootId = null,
                        ),
                    ),
            )

        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.fetch(any<FetchRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        FetchRequest(fetchResult.entries.map { it.id })
                    coEvery { contentService.fetch(any<FetchRequest>()) } returns fetchResult

                    val response =
                        client.post("/v1/fetch") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.fetch(request) }
                    assertEquals(fetchResult, response.body<FetchResult>())
                }
            }
    }

    private companion object {
        private val queryResult =
            QueryResult(
                queryOnId = "queryOnId",
                entries =
                    listOf(
                        QueryResult.Entry(
                            id = "id",
                            content = "content",
                            similarity = 0.9f,
                            authorGID = "authorGID",
                            storedAt = 170000000L,
                            rootId = "rootId",
                        ),
                    ),
            )
        private val searchResult =
            SearchResult(
                queryOnId = "queryOnId",
                entries =
                    listOf(
                        SearchResult.Entry(
                            id = "id",
                            similarity = 0.9f,
                        ),
                    ),
            )
    }
}
