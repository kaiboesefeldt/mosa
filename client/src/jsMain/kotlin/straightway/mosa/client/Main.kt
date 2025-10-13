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
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.p
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityRepository
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
    val (keysText, setKeysText) = useState("Loading identity …")
    val (isBusy, setBusy) = useState(false)
    val effectRan = useRef(false)

    fun refreshIdentity(regenerate: Boolean) {
        if (isBusy) return
        setBusy(true)
        appScope.launch {
            setKeysText(if (regenerate) "Generating new identity …" else "Loading identity …")
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val misJwtService = ClientInjector.get<MisJwtService>()
                val identity =
                    if (regenerate) repository.regenerate()
                    else repository.loadOrCreate()

                var details = identityDetails(identity)
                setKeysText(details)

                val cert = createCertificateFor(identity.gid)
                details += certificateDetails(cert)
                setKeysText(details)

                val jwt = misJwtService.buildMisJwt(identity, cert)
                details += jwtDetails(jwt)
                setKeysText(details)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity refresh failed", error)
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                setKeysText("Failed to prepare identity: $message")
            } finally {
                setBusy(false)
            }
        }
    }

    useEffect(emptyArray<Any>()) {
        if (effectRan.current == true) return@useEffect
        effectRan.current = true
        refreshIdentity(false)
    }

    h1 { +"Mosa – Browser Identity" }
    p { +"This page keeps an Ed25519 key pair in the browser and reuses it across visits." }
    button {
        disabled = isBusy
        onClick = { refreshIdentity(true) }
        +"Generate new identity"
    }
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
    val generatedName = nameGenerator.generate(bits)
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
