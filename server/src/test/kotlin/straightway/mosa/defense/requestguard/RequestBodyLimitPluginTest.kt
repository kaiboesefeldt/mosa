package straightway.mosa.defense.requestguard

import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.testutils.installTestNetworkServerPlugins
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestBodyLimitPluginTest {
    @Test
    fun `rejects oversized request bodies`() =
        testApplication {
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    requestBodyLimitConfig = RequestBodyLimitConfig(maxBodyBytes = 4),
                )
                routing {
                    post("/v1/query") {
                        call.receiveText()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.Authorization, "Bearer token")
                    setBody("12345")
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        }

    @Test
    fun `allows request bodies within the limit`() =
        testApplication {
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    requestBodyLimitConfig = RequestBodyLimitConfig(maxBodyBytes = 4),
                )
                routing {
                    post("/v1/query") {
                        call.receiveText()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.Authorization, "Bearer token")
                    setBody("1234")
                }

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `forwards request body data to the receiver`() =
        testApplication {
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    requestBodyLimitConfig = RequestBodyLimitConfig(maxBodyBytes = 64),
                )
                routing {
                    post("/v1/query") {
                        val payload = call.receiveText()
                        call.respondText(payload)
                    }
                }
            }

            val response =
                client.post("/v1/query") {
                    header(HttpHeaders.Authorization, "Bearer token")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"payload":"ok"}""")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("""{"payload":"ok"}""", response.bodyAsText())
        }

    @Test
    fun `rejects oversized json payload before deserialization`() =
        testApplication {
            application {
                installTestNetworkServerPlugins(
                    requireTlsForPod = false,
                    requestBodyLimitConfig = RequestBodyLimitConfig(maxBodyBytes = 4),
                )
                routing {
                    post("/v1/approvegid") {
                        call.receive<AuthenticationApprovalRequest>()
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.post("/v1/approvegid") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("""{"gid":"abc"}""")
                }

            assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
            assertEquals("""{"message":"Request is larger than the limit of 4 bytes"}""", response.bodyAsText())
        }
}
