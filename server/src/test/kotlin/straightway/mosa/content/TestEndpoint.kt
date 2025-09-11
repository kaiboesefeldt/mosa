package straightway.mosa.content

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

abstract class TestEndpoint(
    val endpoint: String,
) {
    lateinit var env: ControllerHttpTestEnvironment
    var serviceVerification: () -> Unit = {}

    abstract fun setup()

    @Test
    fun `should return 400 BadRequest when invalid request data is specified`() {
        setup()
        env {
            val response =
                client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    setBody("hello")
                    bearerAuth(env.bearerToken)
                }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            serviceVerification()
        }
    }

    @Test
    fun `should return 401 Unauthorized when no bearer token is specified`() {
        setup()
        env {
            val response = client.post(endpoint)
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            serviceVerification()
        }
    }
}
