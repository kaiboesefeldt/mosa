package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals

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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any())
                    }
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.reply(env.gid, request, any()) }
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
                            contentType(ContentType.Application.Json)
                            setBody(request)
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
