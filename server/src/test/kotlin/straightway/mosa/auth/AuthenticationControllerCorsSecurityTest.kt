package straightway.mosa.auth

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import straightway.mosa.content.ControllerHttpTestEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class AuthenticationControllerCorsSecurityTest {
    private val clock =
        Clock.fixed(
            Instant.ofEpochSecond(170000000L),
            ZoneId.of("UTC"),
        )
    private val signatureFactory = TransientEd25519Factory()
    private val podCertificateService = PodCertificateService(signatureFactory, clock)
    private val environment =
        ControllerHttpTestEnvironment(AuthenticationController(podCertificateService, clock, certificateLifeTime)) {
            single { podCertificateService }
            single { clock }
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
                AuthenticationController(podCertificateService, clock, certificateLifeTime),
                requireTlsForPod = false,
            ) {
                single { podCertificateService }
                single { clock }
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
    fun `approvegid returns refresh recommendation timestamp`() =
        environment {
            val response =
                client.post("/v1/approvegid") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        AuthenticationApprovalRequest(
                            gid = "gid".toBase64Url(),
                        ),
                    )
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val certificate = response.body<PodCertificateResponse>()
            assertEquals(expectedRefreshRecommendation, certificate.refreshRecommendedAt)
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

    @Test
    fun `refreshgid rejects unauthenticated request`() =
        environment {
            val response = client.post("/v1/refreshgid")

            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `refreshgid returns certificate with same inf`() =
        environment {
            val inf = "info".toBase64Url()
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = 600L, inf = inf)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val refreshed = response.body<PodCertificateResponse>()
            assertEquals(environment.gid, refreshed.certificate.gid)
            assertEquals(inf, refreshed.certificate.inf)
        }

    @Test
    fun `refreshgid returns refresh recommendation timestamp`() =
        environment {
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = refreshThresholdSeconds - 1)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.Accepted, response.status)
            val refreshed = response.body<PodCertificateResponse>()
            assertEquals(expectedRefreshRecommendation, refreshed.refreshRecommendedAt)
        }

    @Test
    fun `refreshgid rejects certificate that is too fresh`() =
        environment {
            val cert = podCertificateService.approveGid(environment.gid, validitySeconds = certificateLifeTime.inWholeSeconds)
            val token = environment.jwtBuilder.buildMisJwt(environment.clientKeyPair, cert)

            val response =
                client.post("/v1/refreshgid") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(mapOf("message" to "Certificate is too fresh to refresh"), response.body())
        }

    private fun String.toBase64Url(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodeToByteArray())

    private companion object {
        private val certificateLifeTime: Duration = 7.days
        private val refreshThresholdSeconds = certificateLifeTime.inWholeSeconds / 10
        private val expectedRefreshRecommendation = 170000000L + certificateLifeTime.inWholeSeconds - (refreshThresholdSeconds / 2)
    }
}
