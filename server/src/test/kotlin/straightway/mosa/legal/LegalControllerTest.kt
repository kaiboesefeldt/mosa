package straightway.mosa.legal

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.legal.imprint.ImprintAddress
import straightway.mosa.legal.imprint.ImprintContactBlock
import straightway.mosa.legal.imprint.ImprintDocument
import straightway.mosa.legal.imprint.ImprintProviderBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegalControllerTest {
    private val legalService = mockk<LegalService>()
    private val imprintService = mockk<ImprintService>()
    private val environment =
        ControllerHttpTestEnvironment(
            LegalController(legalService, imprintService, LegalStartupStatus({}, {})),
        ) {
            single<LegalService> { legalService }
            single<ImprintService> { imprintService }
        }

    @Test
    fun `returns legal startup status without authentication`() =
        environment {
            val response = client.get("/v1/legal/status")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"complete":true}""", response.body<String>())
        }

    @Test
    fun `returns privacy document chain on v1 legal privacy endpoint`() =
        environment {
            every { legalService.privacyDocumentChainResponse() } returns
                LegalDocumentChainResponse(
                    baseline =
                        buildJsonObject {
                            put("kind", "baseline")
                            put("id", "privacy-baseline")
                        },
                    profiles =
                        listOf(
                            buildJsonObject {
                                put("kind", "profile")
                                put("id", "privacy-default")
                            },
                            buildJsonObject {
                                put("kind", "profile")
                                put("id", "privacy-operator")
                            },
                        ),
                )

            val response =
                client.get("/v1/legal/privacy") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals(
                """{"baseline":{"kind":"baseline","id":"privacy-baseline"},"profiles":[{"kind":"profile","id":"privacy-default"},{"kind":"profile","id":"privacy-operator"}]}""",
                response.body<String>(),
            )
        }

    @Test
    fun `returns terms document chain on v1 legal terms endpoint`() =
        environment {
            every { legalService.termsDocumentChainResponse() } returns
                LegalDocumentChainResponse(
                    baseline =
                        buildJsonObject {
                            put("kind", "baseline")
                            put("id", "terms-baseline")
                        },
                    profiles =
                        listOf(
                            buildJsonObject {
                                put("kind", "profile")
                                put("id", "terms-default")
                            },
                            buildJsonObject {
                                put("kind", "profile")
                                put("id", "terms-operator")
                            },
                        ),
                )

            val response =
                client.get("/v1/legal/terms") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals(
                """{"baseline":{"kind":"baseline","id":"terms-baseline"},"profiles":[{"kind":"profile","id":"terms-default"},{"kind":"profile","id":"terms-operator"}]}""",
                response.body<String>(),
            )
        }

    @Test
    fun `returns imprint document on v1 legal imprint endpoint`() =
        environment {
            every { imprintService.document() } returns
                ImprintDocument(
                    provider = ImprintProviderBlock(name = "Example Pod GmbH"),
                    address =
                        ImprintAddress(
                            street = "Main Street 1",
                            postalCode = "12345",
                            city = "Berlin",
                            country = "Deutschland",
                        ),
                    contact = ImprintContactBlock(email = "legal@example.invalid"),
                )

            val response =
                client.get("/v1/legal/imprint") {
                    bearerAuth("token")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.headers[HttpHeaders.ContentType]?.startsWith("application/json") == true)
            assertEquals(
                ImprintDocument(
                    provider = ImprintProviderBlock(name = "Example Pod GmbH"),
                    address =
                        ImprintAddress(
                            street = "Main Street 1",
                            postalCode = "12345",
                            city = "Berlin",
                            country = "Deutschland",
                        ),
                    contact = ImprintContactBlock(email = "legal@example.invalid"),
                ),
                response.body(),
            )
        }
}
