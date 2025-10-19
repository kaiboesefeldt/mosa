package straightway.mosa.auth

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthenticationControllerCorsSecurityTest {
    private val signatureFactory = TransientEd25519Factory()
    private val podCertificateService = PodCertificateService(signatureFactory)
    private val environment =
        ControllerHttpTestEnvironment(AuthenticationController(podCertificateService)) {
            single { podCertificateService }
        }

    @Test
    fun `approvegid accepts unauthenticated http request`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.XForwardedProto, "http")
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "gid",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
}
