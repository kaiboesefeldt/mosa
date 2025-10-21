package straightway.mosa.webclient

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebClientControllerTest {
    private fun test(
        nullResourceUrl: Boolean = false,
        serveSourceMaps: Boolean = false,
    ) = object {
        val env =
            ControllerHttpTestEnvironment(
                WebClientController(serveSourceMaps) {
                    if (nullResourceUrl) {
                        null
                    } else {
                        ControllerHttpTestEnvironment::class.java.classLoader.getResource(it)
                    }
                },
            )
    }

    @Nested
    inner class RootRoute {
        @Test
        fun `delivers landing page with status OK`() =
            with(test()) {
                env {
                    val response = client.get("/")
                    assertEquals(HttpStatusCode.OK, response.status)
                }
            }

        @Test
        fun `includes the client script`() =
            with(test()) {
                env {
                    val response = client.get("/")
                    assertContains(response.body<String>(), """<div id="root"></div>""")
                    assertContains(response.body<String>(), """<script src="/mosa-client.js"></script>""")
                }
            }

        @Test
        fun `uses address and protocol provides by http host header to identify the pod`() =
            with(test()) {
                env {
                    val response =
                        client.get("/") {
                            headers.set(HttpHeaders.Host, "https://example.com")
                        }
                    assertContains(
                        response.body<String>(),
                        Regex("""<script>\s*window\.MOSA_POD_BASE\s*=\s*"https://example.com";\s*</script>\s*</body>"""),
                    )
                }
            }

        @Test
        fun `adds fallback address of the pod if not provided by HTTP host header`() =
            with(test()) {
                env {
                    val response =
                        client.get("/") {
                            headers.remove(HttpHeaders.Host)
                        }
                    assertContains(
                        response.body<String>(),
                        Regex("""<script>\s*window\.MOSA_POD_BASE\s*=\s*"http://localhost:8080";\s*</script>\s*</body>"""),
                    )
                }
            }

        @Test
        fun `delivers error landing page if regular landing page resource is not available`() =
            with(test(nullResourceUrl = true)) {
                env {
                    val response = client.get("/")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertContains(response.body<String>(), """<h1>Mosa</h1>""")
                    assertContains(response.body<String>(), """Client files not found.""")
                }
            }
    }

    @Nested
    inner class MosaClientJs {
        @Test
        fun `provides client javascript code`() =
            with(test()) {
                env {
                    val response = client.get("/mosa-client.js")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertFalse(response.body<String>().isBlank())
                }
            }

        @Test
        fun `yields error js code if client code cannot obtained`() =
            with(test(nullResourceUrl = true)) {
                env {
                    val response = client.get("/mosa-client.js")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("console.log('Client not built');", response.body<String>())
                }
            }
    }

    @Nested
    inner class MosaClientJsMa {
        @Test
        fun `provides client javascript source map`() =
            with(test(serveSourceMaps = true)) {
                env {
                    val response = client.get("/mosa-client.js.map")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertFalse(response.body<String>().isBlank())
                }
            }

        @Test
        fun `responds with 404, if source map isn't available`() =
            with(test(serveSourceMaps = true, nullResourceUrl = true)) {
                env {
                    val response = client.get("/mosa-client.js.map")
                    assertEquals(HttpStatusCode.NotFound, response.status)
                    assertEquals("", response.body<String>())
                }
            }

        @Test
        fun `responds with 404, if source map isn't served`() =
            with(test(serveSourceMaps = false, nullResourceUrl = false)) {
                env {
                    val response = client.get("/mosa-client.js.map")
                    assertEquals(HttpStatusCode.NotFound, response.status)
                    assertEquals("", response.body<String>())
                }
            }
    }
}
