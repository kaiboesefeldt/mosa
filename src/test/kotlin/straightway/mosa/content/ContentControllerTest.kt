package straightway.mosa.content

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.*
import io.mockk.*
import org.junit.Test
import kotlin.test.assertEquals

class ContentControllerTest {
    private val test get() =
        object {
            val contentService = mockk<ContentService>(relaxed = true)
            val controllerHttpTestEnvironment =
                ControllerHttpTestEnvironment(ContentController(contentService))
        }

    @Test
    fun `publish endpoint should return 201 Created`() =
        with(test) {
            controllerHttpTestEnvironment {
                val request = PublishRequest(text = "Text to publish")
                coEvery { contentService.postContent(any()) } just runs

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

        /*
        @Test
        fun `reply endpoint should return 201 Created`() = testApplication {
            application { setupTestModule() }
            val request = ReplyRequest(/* ... */)
            every { contentService.reply(any()) } just runs

            val response = client.post("/reply") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(ReplyRequest.serializer(), request))
            }

            assertEquals(HttpStatusCode.Created, response.status)
            verify { contentService.reply(request) }
        }

        @Test
        fun `query endpoint should return 200 and result`() = testApplication {
            application { setupTestModule() }
            val request = QueryRequest(/* ... */)
            val expectedResult = QueryResult(/* ... */)
            every { contentService.query(any()) } returns expectedResult

            val response = client.post("/query") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(QueryRequest.serializer(), request))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.decodeFromString(QueryResult.serializer(), response.bodyAsText())
            assertEquals(expectedResult, body)
            verify { contentService.query(request) }
        }

        @Test
        fun `queryon endpoint should return 200 and result`() = testApplication {
            application { setupTestModule() }
            val request = QueryOnRequest(/* ... */)
            val expectedResult = QueryResult(/* ... */)
            every { contentService.queryOn(any()) } returns expectedResult

            val response = client.post("/queryon") {
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(QueryOnRequest.serializer(), request))
            }

            assertEquals(HttpStatusCode.OK, response.status)
            val body = Json.decodeFromString(QueryResult.serializer(), response.bodyAsText())
            assertEquals(expectedResult, body)
            verify { contentService.queryOn(request) }
        }*/
}
