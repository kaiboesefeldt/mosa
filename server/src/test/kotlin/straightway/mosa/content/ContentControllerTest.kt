package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ContentControllerTest {
    private val test get() =
        object {
            val contentService =
                mockk<ContentService>()
            val env =
                ControllerHttpTestEnvironment(ContentController(contentService)) {
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
                        it.contentService.publish(any(), any())
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
                        contentService.publish(env.gid, request)
                    } just runs

                    val response =
                        client.post("/v1/publish") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) {
                        contentService.publish(env.gid, request)
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
                    coVerify(exactly = 0) { it.contentService.reply(any(), any()) }
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
                    coEvery { contentService.reply(env.gid, request) } just runs

                    val response =
                        client.post("/v1/reply") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.reply(env.gid, request) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint : TestEndpoint("/v1/query") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.query(any(), any()) }
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
                            minTime = Instant.ofEpochSecond(1),
                            threadRootId = "12345",
                        )
                    coEvery { contentService.query(any(), any()) } returns queryResult

                    val response =
                        client.post("/v1/query") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.query(env.gid, request) }
                    assertEquals(queryResult, response.body())
                }
            }
    }

    @Nested
    inner class QueryOnEndpoint : TestEndpoint("/v1/queryon") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.queryOn(any(), any()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryOnRequest(queryOnId = "queryOnId")
                    coEvery { contentService.queryOn(any(), any()) } returns queryResult

                    val response =
                        client.post("/v1/queryon") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.queryOn(env.gid, request) }
                    assertEquals(queryResult, response.body())
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
                            storedAt = Instant.ofEpochSecond(170000000L),
                            rootId = "rootId",
                        ),
                    ),
            )
    }
}
