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
}
