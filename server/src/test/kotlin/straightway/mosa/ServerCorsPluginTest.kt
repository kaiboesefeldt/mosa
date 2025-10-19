package straightway.mosa

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ServerCorsPluginTest {
    @Test
    fun `ignores non v1 routes`() =
        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    get("/status") {
                        call.respondText("ok")
                    }
                }
            }

            val response = client.get("/status")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }

    @Test
    fun `rejects unsupported methods`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    delete("/v1/items") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.delete("/v1/items") {
                    header(HttpHeaders.XForwardedProto, "https")
                    header(HttpHeaders.Authorization, "Bearer token")
                }

            assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `rejects non https api calls when authorization present`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    post("/v1/items") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.Created)
                    }
                }
            }

            val response =
                client.post("/v1/items") {
                    header(HttpHeaders.Authorization, "Bearer token")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertNull(response.headers["Access-Control-Allow-Credentials"])
        }
    }
}
