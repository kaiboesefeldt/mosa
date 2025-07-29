package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController

class WebClientController : BaseController() {
    override fun Route.register() {
        get("/") {
            val htmlResource = this::class.java.classLoader.getResource("static/index.html")
            if (htmlResource != null) {
                call.respondText(htmlResource.readText(), ContentType.Text.Html)
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
