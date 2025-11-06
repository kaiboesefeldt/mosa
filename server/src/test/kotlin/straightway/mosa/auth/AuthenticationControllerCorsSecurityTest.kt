package straightway.mosa.auth

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.io.encoding.Base64
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
    fun `approvegid rejects unauthenticated http request when tls required`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.XForwardedProto, "http")
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `approvegid accepts unauthenticated http request when tls allowed`() {
        val httpEnvironment =
            ControllerHttpTestEnvironment(
                AuthenticationController(podCertificateService),
                requireTlsForPod = false,
            ) {
                single { podCertificateService }
            }

        httpEnvironment {
            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.XForwardedProto, "http")
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
    }

    @Test
    fun `approvegid returns bad request when gid is not base64url encoded`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "not base64",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    @Test
    fun `approvegid bad request includes cors headers`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "not base64",
                        ),
                    )
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }

    private fun String.toBase64Url(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodeToByteArray())
}
