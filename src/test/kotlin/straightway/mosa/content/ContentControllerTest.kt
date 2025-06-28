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
                mockk<ContentService>(relaxed = true)
            val controllerHttpTestEnvironment =
                ControllerHttpTestEnvironment(ContentController(contentService))
        }

    @Nested
    inner class PublishEndpoint {
        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request = PublishRequest(text = "Text to publish")

                    val response =
                        client.post("/v1/publish") {
                            contentType(ContentType.Application.Json)
                            setBody(request)
                            bearerAuth("authtoken")
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) { contentService.postContent(request) }
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val response = client.post("/v1/publish")
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.postContent(any()) }
                }
            }
    }

    @Nested
    inner class ReplyEndpoint {
        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val request = ReplyRequest(parentHash = "01234567", text = "Reply")

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

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val response = client.post("/v1/reply")
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.reply(any()) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint {
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
                    val queryResult =
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

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                controllerHttpTestEnvironment {
                    val response = client.post("/v1/query")
                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.reply(any()) }
                }
            }
    }
}
