package straightway.mosa.client

import DeterministicNameGenerator
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.khronos.webgl.get
import org.w3c.dom.HTMLDivElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.pod.podAccess
import straightway.mosa.client.util.Base64
import kotlin.experimental.and

fun main() {
    window.onload = { Main().run() }
}

class Main : KoinComponent {

    private val appScope = MainScope()

    fun run() {
        AppModule.start()

        renderSkeleton()

        appScope.launch {
            val identity = get<IdentityGenerator>().generate()
            showIdentity(identity)

            val cert = createCertificateFor(identity.gid)
            showCertificate(cert)

            val jwt = get<MisJwtService>().buildMisJwt(identity, cert)
            showJwt(jwt)
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

    private val keys by lazy {
        document.getElementById("keys") ?: throw RuntimeException("Element `keys` not found")
    }

    private fun showIdentity(identity: Identity) {
        val nameGenerator = get<DeterministicNameGenerator>()
        val gidBytes = Base64.fromBase64Url(identity.gid)
        val firstByte = gidBytes.get(0)
        val gender = if ((firstByte and 0x1) == 0x1.toByte()) DeterministicNameGenerator.Gender.FEMALE else DeterministicNameGenerator.Gender.MALE
        val lang = if ((firstByte and 0x2) == 0x2.toByte()) DeterministicNameGenerator.Language.DE else DeterministicNameGenerator.Language.EN
        val nameNumber = byteArrayToInt(ByteArray(4) { idx -> gidBytes.get(idx + 1) })
        val generatedName = nameGenerator.generate(
            nameNumber,
            lang,
            gender
        )
        keys.textContent = "Generating Ed25519 key pair …"
        keys.textContent = """
        GID (base64url(sha256(pub))):
        ${identity.gid}
        
        Name:
        ${lang.name} ${generatedName} (${gender.name})
    
        Key Pair:
        ${JSON.stringify(identity.keyPair)}

    """.trimIndent()
    }

    private suspend fun createCertificateFor(gid: String): PodCertificate {
        val podHost = (window.asDynamic().MOSA_POD_BASE as? String) ?: ""
        val podAccess = podAccess(podHost)
        return podAccess.approveGid(gid)
    }

    private fun showCertificate(cert: PodCertificate) {
        keys.textContent += """
        
        Got cert: $cert
        
    """.trimIndent()
    }

    private fun showJwt(jwt: String) {
        keys.textContent += """
        
        MIS-JWT:
        $jwt
    """.trimIndent()
    }

    private fun byteArrayToInt(byteArray: ByteArray): Int {
        var result = 0
        for (i in byteArray.indices) {
            result = result or ((byteArray[i].toInt() and 0xFF) shl (8 * (byteArray.size - 1 - i)))
        }
        return result
    }

}