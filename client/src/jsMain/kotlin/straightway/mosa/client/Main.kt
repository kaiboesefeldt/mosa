package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.JSON
import kotlin.js.json
import org.w3c.dom.HTMLDivElement

fun main() {
    window.onload = {
        renderSkeleton()
        generateAndShowEd25519()
    }
}

private fun renderSkeleton() {
    val body = document.body ?: return
    val heading = document.createElement("h1")
    heading.textContent = "Mosa – Browser Identity"
    body.appendChild(heading)

    val desc = document.createElement("p")
    desc.textContent = "Diese Seite erzeugt lokal im Browser ein Ed25519-Schlüsselpaar."
    body.appendChild(desc)

    val container = document.createElement("div") as HTMLDivElement
    container.id = "keys"
    container.style.whiteSpace = "pre-wrap"
    body.appendChild(container)
}

private fun generateAndShowEd25519() {
    val container = document.getElementById("keys") ?: return
    container.textContent = "Generiere Ed25519-Schlüsselpaar …"

    val generator = IdentityGenerator()
    generator
        .generate()
        .then { id ->
            container.textContent = """
                GID (base64url(sha256(pub))):
                ${id.gid}

                Public Key (raw, base64url):
                ${id.publicB64}

                Private Key (PKCS#8, base64url):
                ${id.privateB64}
            """.trimIndent()

            // Request cert from the same pod
            val podBase = (window.asDynamic().MOSA_POD_BASE as? String)
            val url = podBase?.let { "$it/v1/approvegid" } ?: "/v1/approvegid"
            val reqInit = js("({ method: 'POST', headers: { 'Content-Type': 'application/json; charset=utf-8', 'Accept': 'application/json' } })")
            reqInit.body = JSON.stringify(json("gid" to id.gid))
            window.fetch(url, reqInit)
                .then { resp: dynamic ->
                    if (resp.ok as Boolean) {
                        resp.json().then { cert: dynamic ->
                            container.textContent += "\n\nZertifikat erhalten:\n" + JSON.stringify(cert, null, 2)
                        }
                    } else {
                        container.textContent += "\n\nZertifikat-Anfrage fehlgeschlagen: ${resp.status} ${resp.statusText}"
                    }
                    null
                }
                .catch { err: dynamic ->
                    container.textContent += "\n\nFehler bei Zertifikat-Anfrage: $err"
                }
            null
        }
        .catch { err: dynamic ->
            container.textContent = "Fehler bei der Schlüsselerzeugung: $err"
        }
}
