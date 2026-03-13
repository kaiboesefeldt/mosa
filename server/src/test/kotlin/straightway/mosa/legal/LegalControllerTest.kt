package straightway.mosa.legal

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalControllerTest {
    private val legalService = mockk<LegalService>()
    private val environment =
        ControllerHttpTestEnvironment(
            LegalController(legalService),
        ) {
            single<LegalService> { legalService }
        }

    @Test
    fun `returns canonical baseline json on v1 privacy baseline endpoint`() =
        environment {
            every { legalService.privacyBaselineCanonicalJson() } returns "{\"kind\":\"baseline\"}"

            val response =
                client.get("/v1/legal/privacy/baseline") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"baseline\"}", response.body<String>())
        }

    @Test
    fun `returns canonical default json on v1 privacy default endpoint`() =
        environment {
            every { legalService.privacyDefaultCanonicalJson() } returns "{\"kind\":\"profile\",\"id\":\"default\"}"

            val response =
                client.get("/v1/legal/privacy/default") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"profile\",\"id\":\"default\"}", response.body<String>())
        }

    @Test
    fun `returns canonical mosa json on v1 privacy mosa endpoint`() =
        environment {
            every { legalService.privacyMosaCanonicalJson() } returns "{\"kind\":\"profile\",\"id\":\"mosa\"}"

            val response =
                client.get("/v1/legal/privacy/mosa") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"profile\",\"id\":\"mosa\"}", response.body<String>())
        }

    @Test
    fun `returns canonical service json on v1 privacy operator endpoint`() =
        environment {
            every { legalService.privacyServiceCanonicalJson() } returns "{\"kind\":\"profile\",\"id\":\"service\"}"

            val response =
                client.get("/v1/legal/privacy/operator") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"profile\",\"id\":\"service\"}", response.body<String>())
        }

    @Test
    fun `returns canonical baseline json on v1 terms baseline endpoint`() =
        environment {
            every { legalService.termsBaselineCanonicalJson() } returns "{\"kind\":\"baseline\",\"id\":\"terms-baseline\"}"

            val response =
                client.get("/v1/legal/terms/baseline") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"baseline\",\"id\":\"terms-baseline\"}", response.body<String>())
        }

    @Test
    fun `returns canonical default json on v1 terms default endpoint`() =
        environment {
            every { legalService.termsDefaultCanonicalJson() } returns "{\"kind\":\"profile\",\"id\":\"terms-default\"}"

            val response =
                client.get("/v1/legal/terms/default") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"profile\",\"id\":\"terms-default\"}", response.body<String>())
        }

    @Test
    fun `returns canonical service json on v1 terms service endpoint`() =
        environment {
            every { legalService.termsServiceCanonicalJson() } returns "{\"kind\":\"profile\",\"id\":\"mosa-terms-service\"}"

            val response =
                client.get("/v1/legal/terms/service") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals("{\"kind\":\"profile\",\"id\":\"mosa-terms-service\"}", response.body<String>())
        }
}
