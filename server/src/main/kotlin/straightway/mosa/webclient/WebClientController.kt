package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController

class WebClientController : BaseController() {
    override fun Route.register() {
        get("/") {
            val htmlResource = this::class.java.classLoader.getResource("static/index.html")
            val hostHeader = call.request.header(HttpHeaders.Host) ?: "localhost:8080"
            val proto = call.request.header("X-Forwarded-Proto") ?: "http"
            val base = "$proto://$hostHeader"
            if (htmlResource != null) {
                val html = htmlResource.readText()
                val injected = html.injectMosaPodBase(base)
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
            val jsResource = this::class.java.classLoader.getResource("static/mosa-client.js")
            if (jsResource != null) {
                call.respondText(jsResource.readText(), ContentType.Application.JavaScript)
            } else {
                call.respondText("console.log('Client not built');", ContentType.Application.JavaScript)
            }
        }
    }
}

private fun String.injectMosaPodBase(base: String): String =
    run {
        val marker = "</body>"
        val script =
            """
            <script>
              window.MOSA_POD_BASE = "$base";
            </script>
            """.trimIndent()
        val idx = indexOf(marker, ignoreCase = true)
        if (idx >= 0) {
            substring(0, idx) + script + substring(idx)
        } else {
            this + script
        }
    }
