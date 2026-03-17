package straightway.mosa.message

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
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
                    listOf(AdminMessage("message-1", "admin-gid", 42, "Subject", "Body")),
                )
            coEvery { service.listForRecipient(env.gid) } returns result

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
