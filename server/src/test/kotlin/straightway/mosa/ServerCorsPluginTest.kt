package straightway.mosa

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `rejects put requests`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    put("/v1/items") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.put("/v1/items") {
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

    @Test
    fun `responds to preflight requests`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    get("/v1/items") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.request("/v1/items") {
                    method = HttpMethod.Options
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertEquals("POST, GET, OPTIONS", response.headers[HttpHeaders.AccessControlAllowMethods])
            assertEquals("Authorization, Content-Type", response.headers[HttpHeaders.AccessControlAllowHeaders])
            assertEquals("7200", response.headers[HttpHeaders.AccessControlMaxAge])
        }
    }

    @Test
    fun `allows approve gid route without auth`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin())
                routing {
                    get("/v1/APPROVEGID") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response = client.get("/v1/APPROVEGID")

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `rejects missing authorization for secured endpoint`() {
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
                    header(HttpHeaders.XForwardedProto, "https")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `allows https requests with authorization`() {
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
                    header(HttpHeaders.XForwardedProto, "https")
                    header(HttpHeaders.Authorization, "Bearer token")
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody("{}")
                }

            assertEquals(HttpStatusCode.Created, response.status)
            assertTrue(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }
}
