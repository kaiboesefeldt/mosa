package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
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

    val subtle = window.asDynamic().crypto?.subtle
    if (subtle == null) {
        container.textContent = "WebCrypto nicht verfügbar. Bitte aktuellen Browser verwenden."
        return
    }

    val alg = js("({ name: 'Ed25519' })")
    val usages = arrayOf("sign", "verify")
    val genPromise = subtle.generateKey(alg, true, usages)
    genPromise.then { keyPair: dynamic ->
        val pubKey = keyPair.publicKey
        val privKey = keyPair.privateKey

        // Export public key (raw, 32 bytes) and private key (pkcs8) for display
        val pubExport = subtle.exportKey("raw", pubKey)
        val privExport = subtle.exportKey("pkcs8", privKey)

        pubExport.then { pubBuf: ArrayBuffer ->
            val pubBytes = Uint8Array(pubBuf)
            // GID = base64url(SHA-256(pubRaw))
            val digestPromise = subtle.digest("SHA-256", pubBuf)
            digestPromise.then { digBuf: ArrayBuffer ->
                val gid = toBase64Url(Uint8Array(digBuf))
                privExport.then { priBuf: ArrayBuffer ->
                    val privB64 = toBase64Url(Uint8Array(priBuf))
                    val pubB64 = toBase64Url(pubBytes)
                    container.textContent = """
                        GID (base64url(sha256(pub))):
                        $gid

                        Public Key (raw, base64url):
                        $pubB64

                        Private Key (PKCS#8, base64url):
                        $privB64
                    """.trimIndent()
                    // Request cert from the same pod
                    val podBase = (window.asDynamic().MOSA_POD_BASE as? String) ?: window.location.origin
                    val reqInit = js("({ method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ gid: gid }) })")
                    val url = "$podBase/v1/approvegid"
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
                }.catch { err: dynamic ->
                    container.textContent = "Fehler beim Export des privaten Schlüssels: $err"
                }
            }.catch { err: dynamic ->
                container.textContent = "Fehler beim Hashen des öffentlichen Schlüssels: $err"
            }
        }.catch { err: dynamic ->
            container.textContent = "Fehler beim Export des öffentlichen Schlüssels: $err"
        }
        null
    }.catch { err: dynamic ->
        container.textContent = "Fehler bei der Schlüsselerzeugung: $err"
    }
}

private fun toBase64Url(bytes: Uint8Array): String {
    // Convert bytes to binary string
    val len = bytes.length
    val sb = StringBuilder(len)
    var i = 0
    while (i < len) {
        val v = (bytes.asDynamic()[i] as Int)
        sb.append(v.toChar())
        i++
    }
    val binary = sb.toString()
    val b64 = window.btoa(binary)
    return b64.replace('+', '-').replace('/', '_').trimEnd('=')
}
