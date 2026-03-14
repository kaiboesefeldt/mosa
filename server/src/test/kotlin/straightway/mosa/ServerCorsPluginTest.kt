package straightway.mosa

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
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import straightway.mosa.network.ClientIpResolver
import straightway.mosa.network.createMosaApiCorsPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerCorsPluginTest {
    private val trustedProxyResolver = ClientIpResolver(setOf("localhost"))

    @Test
    fun `ignores non v1 routes`() =
        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    get("/status") {
                        call.respondText("ok")
                    }
                }
            }

            val response =
                client.get("/status") {
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }

    @Test
    fun `rejects unsupported methods`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    put("/v1/items") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.request("/v1/items") {
                    method = HttpMethod.Patch
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
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
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
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
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
                    header(HttpHeaders.Host, "example.com")
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
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
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
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
            assertEquals("POST, GET, DELETE, OPTIONS", response.headers[HttpHeaders.AccessControlAllowMethods])
            assertEquals("Authorization, Content-Type", response.headers[HttpHeaders.AccessControlAllowHeaders])
            assertEquals("7200", response.headers[HttpHeaders.AccessControlMaxAge])
        }
    }

    @Test
    fun `allows POST approve gid route without auth`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    post("/v1/APPROVEGID") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.post("/v1/APPROVEGID") {
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `doesn't allow GET approve gid route without auth`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    get("/v1/APPROVEGID") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.get("/v1/APPROVEGID") {
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertFalse(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `allows GET legal privacy baseline route without auth`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    get("/v1/legal/privacy/baseline") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.get("/v1/legal/privacy/baseline") {
                    header(HttpHeaders.XForwardedProto, "https")
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(handlerInvoked)
            assertEquals("*", response.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals("Retry-After", response.headers[HttpHeaders.AccessControlExposeHeaders])
        }
    }

    @Test
    fun `allows GET legal privacy chain route without auth`() {
        var handlerInvoked = false

        testApplication {
            application {
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
                routing {
                    get("/v1/legal/privacy") {
                        handlerInvoked = true
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            val response =
                client.get("/v1/legal/privacy") {
                    header(HttpHeaders.XForwardedProto, "https")
                }

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
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
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
                install(createMosaApiCorsPlugin(requireTlsForPod = true, clientIpResolver = trustedProxyResolver))
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
