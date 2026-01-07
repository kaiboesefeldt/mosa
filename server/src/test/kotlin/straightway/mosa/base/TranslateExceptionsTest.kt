package straightway.mosa.base

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.routing.Route
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.test.assertEquals

class TranslateExceptionsTest {
    private val env =
        ControllerHttpTestEnvironment(
            object : BaseController() {
                override fun Route.register() {
                    getRoute("payload-too-large") {
                        throw PayloadTooLargeException(10L)
                    }
                    getRoute("unexpected-error") {
                        throw IllegalStateException("boom")
                    }
                }
            },
        )

    @Test
    fun `translates payload too large exception into 413 response`() =
        env {
            val response =
                client.get("/v1/payload-too-large") {
                    bearerAuth(env.bearerToken)
                }
            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals(
                mapOf("message" to "Request is larger than the limit of 10 bytes"),
                response.body<Map<String, String>>(),
            )
        }

    @Test
    fun `rethrows non payload exception and returns 500`() =
        env {
            val response =
                client.get("/v1/unexpected-error") {
                    bearerAuth(env.bearerToken)
                }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }
}
