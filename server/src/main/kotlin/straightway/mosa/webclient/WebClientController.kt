package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController
import java.net.URL

class WebClientController(
    val serveSourceMaps: Boolean = false,
    private val getResourceUrl: (String) -> URL? = { WebClientController::class.java.classLoader.getResource(it) },
) : BaseController() {
    override fun Route.register() {
        get("/") {
            val htmlResourceUrl = getResourceUrl("static/index.html") ?: throw RuntimeException("internal resource not found")
            val hostHeader = call.request.header(HttpHeaders.Host) ?: throw BadRequestException("No Host header")
            val proto = call.request.header("X-Forwarded-Proto") ?: defaultProtoForHost(hostHeader)
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
    }

    private fun defaultProtoForHost(host: String): String = if (host.startsWith("localhost", ignoreCase = true)) "http" else "https"

    private val clientCodeJs: String by lazy {
        getResourceUrl("static/mosa-client.js")?.readText() ?: "console.log('Client not built');"
    }

    private val mapResource: String? by lazy {
        getResourceUrl("static/mosa-client.js.map")?.readText()
    }

    private companion object {
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
