package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.mockk.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ContentControllerTest {
    private val test get() =
        object {
            val contentService =
                mockk<ContentService>()
            val controllerHttpTestEnvironment =
                ControllerHttpTestEnvironment(ContentController(contentService))
        }

    @Nested
    inner class PublishEndpoint : TestEndpoint("/v1/publish") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.publish(any()) }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        PublishRequest(
                            authorGID = "author GID",
                            text = "Text to publish",
                            receivedAt = 170000000,
                        )
                    coEvery { contentService.publish(request) } just runs

                    val response =
                        client.post("/v1/publish") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("authtoken")
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.publish(request) }
                }
            }
    }

    @Nested
    inner class ReplyEndpoint : TestEndpoint("/v1/reply") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.reply(any()) }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        ReplyRequest(
                            content =
                                PublishRequest(
                                    authorGID = "author GID",
                                    text = "Text to publish",
                                    receivedAt = 170000000,
                                ),
                            parentHash = "01234567",
                        )
                    coEvery { contentService.reply(request) } just runs

                    val response =
                        client.post("/v1/reply") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("authtoken")
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.reply(request) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint : TestEndpoint("/v1/query") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.query(any()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        QueryRequest(
                            embeddingVersion = "1",
                            embeddingVector = listOf(1.0f, 2.0f, 3.0f),
                            minSimilarity = 0.8f,
                            maxResults = 10,
                        )
                    coEvery { contentService.query(any()) } returns queryResult

                    val response =
                        client.post("/v1/query") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("authtoken")
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.query(request) }
                    assertEquals(queryResult, response.body())
                }
            }
    }

    @Nested
    inner class QueryOnEndpoint : TestEndpoint("/v1/queryon") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.queryOn(any()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        QueryOnRequest(
                            queryOnId = "queryOnId",
                            maxResults = 10,
                        )
                    coEvery { contentService.queryOn(any()) } returns queryResult

                    val response =
                        client.post("/v1/queryon") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("authtoken")
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.queryOn(request) }
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
                            storedAt = 170000000,
                            parent = "parent",
                        ),
                    ),
            )
    }
}
