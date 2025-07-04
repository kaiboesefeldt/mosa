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
                    coVerify(exactly = 0) { it.contentService.publish(any(), any()) }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                        )
                    coEvery { contentService.publish("userGID", request) } just runs

                    val response =
                        client.post("/v1/publish") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("userGID")
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.publish("userGID", request) }
                }
            }
    }

    @Nested
    inner class ReplyEndpoint : TestEndpoint("/v1/reply") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.reply(any(), any()) }
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
                                    text = "Text to publish",
                                ),
                            parentId = "01234567",
                        )
                    coEvery { contentService.reply("userGID", request) } just runs

                    val response =
                        client.post("/v1/reply") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("userGID")
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.reply("userGID", request) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint : TestEndpoint("/v1/query") {
        override fun setup() {
            test.also {
                controllerHttpTestEnvironment = it.controllerHttpTestEnvironment
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.query(any(), any()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        SearchRequest(
                            embeddingVersion = "1",
                            embeddingVector = listOf(1.0f, 2.0f, 3.0f),
                            minSimilarity = 0.8f,
                            maxResults = 10,
                        )
                    coEvery { contentService.query(any(), any()) } returns queryResult

                    val response =
                        client.post("/v1/query") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("userGID")
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.query("userGID", request) }
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
                    coVerify(exactly = 0) { it.contentService.queryOn(any(), any()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request =
                        SearchOnRequest(
                            searchOnId = "queryOnId",
                            maxResults = 10,
                        )
                    coEvery { contentService.queryOn(any(), any()) } returns queryResult

                    val response =
                        client.post("/v1/queryon") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("userGID")
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.queryOn("userGID", request) }
                    assertEquals(queryResult, response.body())
                }
            }
    }

    private companion object {
        private val queryResult =
            SearchResult(
                searchOnId = "queryOnId",
                entries =
                    listOf(
                        SearchResult.Entry(
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
