package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLDivElement
import org.koin.core.component.KoinComponent

private val appScope = MainScope()

fun main() {
    window.onload = {
        ClientDI.start()
        renderSkeleton()

        appScope.launch {
            val identity = KoinHolder.identityGenerator.generate()
            showIdentity(identity)
            val cert = createCertificateFor(identity.gid)
            showCertificate(cert)
            val jwt = buildMisJwt(identity, cert)
            showJwt(jwt)
        }
    }
}

private fun renderSkeleton() {
    val body = document.body ?: return
    val heading = document.createElement("h1")
    heading.textContent = "Mosa – Browser Identity"
    body.appendChild(heading)

    val desc = document.createElement("p")
    desc.textContent = "This page generates an Ed25519 key pair locally in the browser."
    body.appendChild(desc)

    val container = document.createElement("div") as HTMLDivElement
    container.id = "keys"
    container.style.whiteSpace = "pre-wrap"
    body.appendChild(container)
}

private val keys by lazy { document.getElementById("keys") ?: throw RuntimeException("Element `keys` not found") }

private fun showIdentity(identity: Identity) {
    keys.textContent = "Generating Ed25519 key pair …"
    keys.textContent = """
        GID (base64url(sha256(pub))):
        ${identity.gid}
    
        Key Pair:
        ${identity.keyPair}

    """.trimIndent()
}

private suspend fun createCertificateFor(gid: String): PodCertificate {
    val podHost = (window.asDynamic().MOSA_POD_BASE as? String) ?: ""
    val podAccess = KoinHolder.pod(podHost)
    return podAccess.approveGid(gid)
}

private fun showCertificate(cert: PodCertificate) {
    keys.textContent += """
        
        Got cert: $cert
        
    """.trimIndent()
}

private object KoinHolder : KoinComponent

private fun showJwt(jwt: String) {
    keys.textContent += """
        
        MIS-JWT:
        $jwt
    """.trimIndent()
}
