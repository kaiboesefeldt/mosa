package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityGenerator
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.pod.podAccess
import straightway.mosa.client.util.Base64
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.util.BitStream
import web.dom.Element as WebElement
import kotlin.js.unsafeCast

private val appScope = MainScope()

fun main() {
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}

private val IdentityApp = FC<Props> {
    val (keysText, setKeysText) = useState("Generating Ed25519 key pair …")
    val effectRan = useRef(false)

    useEffect(emptyArray<Any>()) {
        if (effectRan.current == true) return@useEffect
        effectRan.current = true
        appScope.launch {
            val identity = ClientInjector.get<IdentityGenerator>().generate()
            setKeysText(identityDetails(identity))

            val cert = createCertificateFor(identity.gid)
            setKeysText { current -> current + certificateDetails(cert) }

            val jwt = ClientInjector.get<MisJwtService>().buildMisJwt(identity, cert)
            setKeysText { current -> current + jwtDetails(jwt) }
        }
    }

    h1 { +"Mosa – Browser Identity" }
    p { +"This page generates an Ed25519 key pair locally in the browser." }
    div {
        val attributes = this.unsafeCast<dynamic>()
        attributes.id = "keys"
        +keysText
    }
}

private object ClientInjector : KoinComponent

private fun identityDetails(identity: Identity): String {
    val nameGenerator = ClientInjector.get<FictionalNameGenerator>()
    val gidBytes = Base64.fromBase64Url(identity.gid)
    val bits = BitStream(gidBytes)
    val generatedName = nameGenerator[bits.nextLong(nameGenerator.size + 1)]
    return """
        GID (base64url(sha256(pub))):
        ${identity.gid}

        Name:
        ${generatedName} out of ${nameGenerator.size} (${bits.consumedBits} Bits consumed)

        Key Pair:
        ${JSON.stringify(identity.keyPair)}

    """.trimIndent()
}

private suspend fun createCertificateFor(gid: String): PodCertificate {
    val podHost = (window.asDynamic().MOSA_POD_BASE as? String) ?: ""
    val podAccess = ClientInjector.podAccess(podHost)
    return podAccess.approveGid(gid)
}

private fun certificateDetails(cert: PodCertificate): String = "\n\nGot cert: $cert\n\n"

private fun jwtDetails(jwt: String): String = "\n\nMIS-JWT:\n$jwt"
