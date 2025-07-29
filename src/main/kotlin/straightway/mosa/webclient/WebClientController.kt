package straightway.mosa.webclient

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import straightway.mosa.base.BaseController

class WebClientController : BaseController() {
    override fun Route.register() {
        get("/") {
            val html =
                """
                <!DOCTYPE html>
                <html lang="de">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Mosa</title>
                </head>
                <body>
                    <div id="root"></div>
                    <script>
                        document.getElementById("root").innerHTML = "<h1>Mosa</h1>";
                    </script>
                </body>
                </html>
                """.trimIndent()
            call.respondText(html, ContentType.Text.Html)
        }
    }
}
