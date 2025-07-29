package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window

fun main() {
    window.onload = {
        val body = document.body
        if (body != null) {
            val heading = document.createElement("h1")
            heading.textContent = "Mosa"
            body.appendChild(heading)
        }
    }
}