package straightway.mosa.logging

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import straightway.mosa.base.BaseController
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestNumberMdcTest {
    private val env =
        ControllerHttpTestEnvironment(
            object : BaseController() {
                override fun Route.register() {
                    getRoute("mdc") {
                        val requestNumberFromMdc = withContext(Dispatchers.Default) { MDC.get("reqNo") }
                        call.respondText(requestNumberFromMdc.orEmpty())
                    }
                }
            },
        )

    @Test
    fun `adds request number to MDC in route handlers`() =
        env {
            val response =
                client.get("/v1/mdc") {
                    bearerAuth(env.bearerToken)
                }
            assertEquals(HttpStatusCode.OK, response.status)

            val requestNumber = response.bodyAsText()
            assertNotNull(requestNumber)
            assertTrue { Regex("req#\\d+").matches(requestNumber) }
        }
}
