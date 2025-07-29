package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController
import java.io.File

class WebClientController : BaseController() {
    override fun Route.register() {
        get("/") {
            val htmlFile = File("build/resources/main/static/index.html")
            if (htmlFile.exists()) {
                call.respondText(htmlFile.readText(), ContentType.Text.Html)
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
            val jsFile = File("build/resources/main/static/mosa-client.js")
            if (jsFile.exists()) {
                call.respondText(jsFile.readText(), ContentType.Application.JavaScript)
            } else {
                call.respondText("console.log('Client not built');", ContentType.Application.JavaScript)
            }
        }
    }
}
