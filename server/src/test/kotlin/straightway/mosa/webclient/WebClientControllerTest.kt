package straightway.mosa.webclient

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.io.path.createTempDirectory
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WebClientControllerTest {
    private fun test(
        nullResourceUrl: Boolean = false,
        serveSourceMaps: Boolean = false,
        resourceDirectory: Path? = null,
        allowPlainHttp: Boolean = false,
        requireTlsForPod: Boolean = true,
    ) = object {
        val env =
            ControllerHttpTestEnvironment(
                WebClientController(
                    serveSourceMaps = serveSourceMaps,
                    allowPlainHttp = allowPlainHttp,
                    getResourceUrl = { resourceName ->
                        when {
                            nullResourceUrl -> null
                            resourceDirectory != null -> {
                                val path = resourceDirectory.resolve(resourceName)
                                if (Files.exists(path)) {
                                    path.toUri().toURL()
                                } else {
                                    null
                                }
                            }
                            else -> ControllerHttpTestEnvironment::class.java.classLoader.getResource(resourceName)
                        }
                    },
                ),
                requireTlsForPod = requireTlsForPod,
            )
    }

    @Nested
    inner class RootRoute {
        @Test
        fun `delivers landing page with status OK`() =
            with(test()) {
                env {
                    val response = client.get("/") { headers.set(HttpHeaders.Host, "example.com") }
                    assertEquals(HttpStatusCode.OK, response.status)
                }
            }

        @Test
        fun `includes the client script`() =
            with(test()) {
                env {
                    val response = client.get("/") { headers.set(HttpHeaders.Host, "example.com") }
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
                            headers.set(HttpHeaders.Host, "example.com")
                            headers.set("X-Forwarded-Proto", "https")
                        }
                    assertContains(
                        response.body<String>(),
                        Regex("""<script>\s*window\.MOSA_POD_BASE\s*=\s*"https://example.com";\s*</script>\s*</body>"""),
                    )
                }
            }

        @Test
        fun `returns 400 Bad Request if no host header present`() =
            with(test()) {
                env {
                    val response =
                        client.get("/") {
                            headers.remove(HttpHeaders.Host)
                        }
                    assertEquals(HttpStatusCode.BadRequest, response.status)
                }
            }

        @Test
        fun `return 500 Internal Error if regular landing page resource is not available`() =
            with(test(nullResourceUrl = true)) {
                env {
                    val response = client.get("/")
                    assertEquals(HttpStatusCode.InternalServerError, response.status)
                }
            }

        @Test
        fun `defaults to https when no forwarded proto header present`() =
            with(test(requireTlsForPod = false)) {
                env {
                    val bareClient = createClient {}
                    try {
                        val response =
                            bareClient.get("/") {
                                headers.set(HttpHeaders.Host, "example.com")
                            }
                        assertContains(
                            response.bodyAsText(),
                            Regex("""window\.MOSA_POD_BASE\s*=\s*"https://example.com";"""),
                        )
                    } finally {
                        bareClient.close()
                    }
                }
            }

        @Test
        fun `defaults to https for localhost hosts when plain http disabled`() =
            with(test(requireTlsForPod = false)) {
                env {
                    val bareClient = createClient {}
                    try {
                        val response =
                            bareClient.get("/") {
                                headers.set(HttpHeaders.Host, "localhost:8080")
                            }
                        assertContains(
                            response.bodyAsText(),
                            Regex("""window\.MOSA_POD_BASE\s*=\s*"https://localhost:8080";"""),
                        )
                    } finally {
                        bareClient.close()
                    }
                }
            }

        @Test
        fun `uses http for localhost hosts when plain http enabled`() =
            with(test(allowPlainHttp = true, requireTlsForPod = false)) {
                env {
                    val bareClient = createClient {}
                    try {
                        val response =
                            bareClient.get("/") {
                                headers.set(HttpHeaders.Host, "localhost:8080")
                            }
                        assertContains(
                            response.bodyAsText(),
                            Regex("""window\.MOSA_POD_BASE\s*=\s*"http://localhost:8080";"""),
                        )
                    } finally {
                        bareClient.close()
                    }
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

    @Nested
    inner class Stylesheet {
        @Test
        fun `delivers global stylesheet`() =
            with(test()) {
                env {
                    val response = client.get("/styles.css")
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertContains(response.body<String>(), "--font-family")
                }
            }

        @Test
        fun `responds with 404 when stylesheet is missing`() =
            with(test(nullResourceUrl = true)) {
                env {
                    val response = client.get("/styles.css")
                    assertEquals(HttpStatusCode.NotFound, response.status)
                    assertEquals("", response.body<String>())
                }
            }
    }

    @Nested
    inner class Webfonts {
        @Test
        fun `responds with 404 when font query misses parameter`() =
            createResourceDirectory().use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/webfonts/")
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertEquals("", response.body<String>())
                    }
                }
            }

        @Test
        fun `rejects font requests with directory traversal`() =
            createResourceDirectory().use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/webfonts/%2E%2E%2Ffont.woff2")
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertEquals("", response.body<String>())
                    }
                }
            }

        @Test
        fun `responds with 404 when font file is unknown`() =
            createResourceDirectory().use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/webfonts/font.woff2")
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertEquals("", response.body<String>())
                    }
                }
            }

        @Test
        fun `serves font bytes when available`() =
            createResourceDirectory("static/webfonts/font.woff2" to byteArrayOf(1, 2, 3)).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/webfonts/font.woff2")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType("font", "woff2"), response.contentType())
                        assertContentEquals(byteArrayOf(1, 2, 3), response.body())
                    }
                }
            }
    }

    @Nested
    inner class StaticAsset {
        @Test
        fun `rejects asset requests with directory traversal`() =
            createResourceDirectory().use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/%2E%2E%2Fsecret.txt")
                        assertEquals(HttpStatusCode.BadRequest, response.status)
                        assertEquals("", response.body<String>())
                    }
                }
            }

        @Test
        fun `responds with 404 when asset is missing`() =
            createResourceDirectory().use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/missing.js")
                        assertEquals(HttpStatusCode.NotFound, response.status)
                        assertEquals("", response.body<String>())
                    }
                }
            }

        @Test
        fun `uses javascript content type for js assets`() =
            createResourceDirectory("static/script.js" to "console.log('ok')".encodeToByteArray()).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/script.js")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType.Application.JavaScript, response.contentType())
                        assertEquals("console.log('ok')", response.body<String>())
                    }
                }
            }

        @Test
        fun `uses json content type for js map assets`() =
            createResourceDirectory("static/script.js.map" to """{"version":3}""".encodeToByteArray()).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/script.js.map")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType.Application.Json, response.contentType())
                        assertEquals("""{"version":3}""", response.body<String>())
                    }
                }
            }

        @Test
        fun `uses css content type for styles`() =
            createResourceDirectory("static/theme.css" to "body {}".encodeToByteArray()).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/theme.css")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType.Text.CSS, response.contentType())
                        assertEquals("body {}", response.body<String>())
                    }
                }
            }

        @Test
        fun `uses font content type for woff2 assets`() =
            createResourceDirectory("static/font.woff2" to byteArrayOf(5, 6, 7)).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/font.woff2")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType("font", "woff2"), response.contentType())
                        assertContentEquals(byteArrayOf(5, 6, 7), response.body())
                    }
                }
            }

        @Test
        fun `falls back to octet stream for unknown asset types`() =
            createResourceDirectory("static/data.bin" to byteArrayOf(9, 8, 7)).use { resources ->
                with(test(resourceDirectory = resources.path)) {
                    env {
                        val response = client.get("/data.bin")
                        assertEquals(HttpStatusCode.OK, response.status)
                        assertEquals(ContentType.Application.OctetStream, response.contentType())
                        assertContentEquals(byteArrayOf(9, 8, 7), response.body())
                    }
                }
            }
    }
}

private fun createResourceDirectory(vararg resources: Pair<String, ByteArray>): TempResourceDirectory {
    val directory = createTempDirectory("webclient-resources")
    resources.forEach { (name, content) ->
        val path = directory.resolve(name)
        Files.createDirectories(path.parent)
        Files.write(path, content)
    }
    return TempResourceDirectory(directory)
}

private class TempResourceDirectory(
    val path: Path,
) : AutoCloseable {
    override fun close() {
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
