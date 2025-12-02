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
                    } returns ""

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
                        contentService.publish(any(), any())
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
                    coEvery { contentService.reply(env.gid, request) } returns ""

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
                            minTime = 1L,
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
                            storedAt = 170000000L,
                            rootId = "rootId",
                        ),
                    ),
            )
    }
}
