package straightway.mosa.message

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.testutils.setJsonBody
import kotlin.test.assertEquals

class AdminMessageControllerTest {
    private val service = mockk<AdminMessageService>()
    private val env = ControllerHttpTestEnvironment(AdminMessageController(service))

    @Test
    fun `post admin messages returns 204 and forwards sender gid`() =
        runTest {
            val request = CreateAdminMessageRequest("recipient-gid", "Subject", "Body")
            coEvery { service.send(env.gid, request) } returns Unit

            env {
                val response =
                    client.post("/v1/admin/messages") {
                        bearerAuth(env.bearerToken)
                        setJsonBody(request)
                    }

                assertEquals(HttpStatusCode.NoContent, response.status)
                coVerify(exactly = 1) { service.send(env.gid, request) }
            }
        }

    @Test
    fun `get messages returns inbox result for authenticated user`() =
        runTest {
            val result =
                AdminMessageListResult(
                    listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body", 5)),
                    page = 3,
                    hasMore = true,
                )
            coEvery { service.listInboxForRecipient(env.gid, 3, 0) } returns result

            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("page", "3")
                        parameter("fromTime", "0")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(result, response.body<AdminMessageListResult>())
            }
        }

    @Test
    fun `get messages forwards fromTime`() =
        runTest {
            val result = AdminMessageListResult(emptyList())
            coEvery { service.listInboxForRecipient(env.gid, 1, 1234) } returns result

            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("fromTime", "1234")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(result, response.body<AdminMessageListResult>())
            }
        }

    @Test
    fun `get admin messages returns paged history for authenticated admin`() =
        runTest {
            val result =
                AdminMessageListResult(
                    listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body", 5)),
                    page = 2,
                    hasMore = false,
                )
            coEvery {
                service.listAdminMessagesForRecipient(
                    requesterGid = env.gid,
                    recipientGid = "recipient-gid",
                    page = 2,
                )
            } returns result

            env {
                val response =
                    client.get("/v1/admin/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("gid", "recipient-gid")
                        parameter("page", "2")
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(result, response.body<AdminMessageListResult>())
            }
        }

    @Test
    fun `get messages rejects non numeric page`() =
        runTest {
            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("page", "abc")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

    @Test
    fun `get messages rejects negative fromTime`() =
        runTest {
            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("fromTime", "-1")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

    @Test
    fun `get messages rejects non numeric fromTime`() =
        runTest {
            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                        parameter("fromTime", "abc")
                    }

                assertEquals(HttpStatusCode.BadRequest, response.status)
            }
        }

    @Test
    fun `get messages uses default fromTime when omitted`() =
        runTest {
            val result = AdminMessageListResult(emptyList())
            coEvery { service.listInboxForRecipient(env.gid, 1, 0) } returns result

            env {
                val response =
                    client.get("/v1/messages") {
                        bearerAuth(env.bearerToken)
                    }

                assertEquals(HttpStatusCode.OK, response.status)
                assertEquals(result, response.body<AdminMessageListResult>())
            }
        }
}
