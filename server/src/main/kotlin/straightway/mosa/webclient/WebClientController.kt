package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController
import java.net.URL

class WebClientController(
    val serveSourceMaps: Boolean = false,
    allowPlainHttp: Boolean = false,
    private val getResourceUrl: (String) -> URL? = { WebClientController::class.java.classLoader.getResource(it) },
) : BaseController() {
    override fun Route.register() {
        get("/") {
            val htmlResourceUrl = getResourceUrl("static/index.html") ?: throw RuntimeException("internal resource not found")
            val hostHeader = call.request.header(HttpHeaders.Host) ?: throw BadRequestException("No Host header")
            val proto = call.request.header("X-Forwarded-Proto") ?: defaultProto
            val base = "$proto://$hostHeader"
            val html = htmlResourceUrl.readText()
            val injected = html.injectMosaPodBase(base)
            call.respondText(injected, ContentType.Text.Html)
        }

        get("/mosa-client.js") {
            call.respondText(clientCodeJs, ContentType.Application.JavaScript)
        }

        get("/mosa-client.js.map") {
            val mapContent = mapResource
            if (serveSourceMaps && mapContent != null) {
                call.respondText(mapContent, ContentType.Application.Json)
            } else {
                call.respondText("", ContentType.Application.Json, HttpStatusCode.NotFound)
            }
        }

        get("/styles.css") {
            val css = styleResource
            if (css != null) {
                call.respondText(css, ContentType.Text.CSS)
            } else {
                call.respondText("", ContentType.Text.CSS, HttpStatusCode.NotFound)
            }
        }

        get("/webfonts/{fontFile}") {
            val fontFile =
                call.parameters["fontFile"] ?: return@get call.respondText(
                    "",
                    ContentType.Text.Plain,
                    HttpStatusCode.NotFound,
                )
            if (fontFile.contains("/") || fontFile.contains("..")) {
                return@get call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadRequest)
            }
            val fontResource =
                getResourceUrl("static/webfonts/$fontFile")
                    ?: return@get call.respondText("", ContentType.Text.Plain, HttpStatusCode.NotFound)
            val fontBytes = fontResource.openStream().use { it.readBytes() }
            call.respondBytes(fontBytes, ContentType("font", "woff2"))
        }

        get("/{assetName}") {
            val assetName =
                call.parameters["assetName"] ?: return@get call.respondText(
                    "",
                    ContentType.Text.Plain,
                    HttpStatusCode.NotFound,
                )
            if (assetName.contains("/") || assetName.contains("..")) {
                return@get call.respondText("", ContentType.Text.Plain, HttpStatusCode.BadRequest)
            }
            val assetResource =
                getResourceUrl("static/$assetName") ?: return@get call.respondText(
                    "",
                    ContentType.Text.Plain,
                    HttpStatusCode.NotFound,
                )
            val assetBytes = assetResource.openStream().use { it.readBytes() }
            val contentType = assetName.determineContentType()
            call.respondBytes(assetBytes, contentType)
        }
    }

    private val defaultProto = if (allowPlainHttp) "http" else "https"

    private val clientCodeJs: String by lazy {
        getResourceUrl("static/mosa-client.js")?.readText() ?: "console.log('Client not built');"
    }

    private val mapResource: String? by lazy {
        getResourceUrl("static/mosa-client.js.map")?.readText()
    }

    private val styleResource: String? by lazy {
        getResourceUrl("static/styles.css")?.readText()
    }

    private companion object {
        private fun String.determineContentType(): ContentType =
            when {
                endsWith(".js") -> ContentType.Application.JavaScript
                endsWith(".js.map") -> ContentType.Application.Json
                endsWith(".css") -> ContentType.Text.CSS
                endsWith(".woff2") -> ContentType("font", "woff2")
                else -> ContentType.Application.OctetStream
            }

        private fun String.injectMosaPodBase(base: String): String =
            run {
                val marker = "</body>"
                val script =
                    """
                      <script>
                        window.MOSA_POD_BASE = "$base";
                      </script>
                    $marker
                    """.trimIndent()
                replace(marker, script)
            }
    }
}
