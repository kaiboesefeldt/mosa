package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
            val htmlResourceUrl = getResourceUrl("static/index.html")
            val hostHeader = call.request.header(HttpHeaders.Host) ?: "http://localhost:8080"
            if (htmlResourceUrl != null) {
                val html = htmlResourceUrl.readText()
                val injected = html.injectMosaPodBase(hostHeader)
                call.respondText(injected, ContentType.Text.Html)
            } else {
                val fallbackHtml =
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Mosa Client</title>
                    </head>
                    <body>
                        <h1>Mosa</h1>
                        <p>Client files not found. Run './gradlew build' to generate them.</p>
                    </body>
                    </html>
                    """.trimIndent()
                call.respondText(fallbackHtml, ContentType.Text.Html)
            }
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
