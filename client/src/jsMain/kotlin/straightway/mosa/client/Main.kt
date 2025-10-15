package straightway.mosa.client

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.CSSProperties
import react.FC
import react.Props
import react.create
import react.dom.client.Root
import react.dom.client.createRoot
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.pre
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.small
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
import kotlin.js.json
import kotlin.js.unsafeCast
import web.dom.Element as WebElement

private val appScope = MainScope()

fun main() {
    AppModule.start()
    val rootElement =
        document.getElementById("root")?.unsafeCast<WebElement>() ?: error("Element `root` not found")
    val root: Root = createRoot(rootElement)
    root.render(IdentityApp.create())
}

private val IdentityApp = FC<Props> {
    val (identityText, setIdentityText) = useState("...")
    val (currentIdentity, setCurrentIdentity) = useState<Identity?>(null)
    val (misJwt, setMisJwt) = useState<String?>(null)
    val (isConnected, setConnected) = useState(false)
    val (isBusy, setBusy) = useState(false)
    val (errorText, setErrorText) = useState<String?>(null)
    val effectRan = useRef(false)
    val defaultPod = (window.asDynamic().MOSA_POD_BASE as? String).orEmpty()
    val pods = listOfNotNull(defaultPod.takeIf { it.isNotBlank() })
    val (selectedPod, setSelectedPod) = useState(defaultPod)

    fun refreshIdentity(regenerate: Boolean) {
        if (isBusy) return
        setBusy(true)
        appScope.launch {
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val identity =
                    if (regenerate) repository.regenerate()
                    else repository.loadOrCreate()
                val resolvedName = identityName(identity)
                setIdentityText(resolvedName)
                setCurrentIdentity(identity)
                setMisJwt(null)
                setConnected(false)
                setErrorText(null)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity refresh failed", error)
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                setIdentityText("Failed to prepare identity: $message")
                setCurrentIdentity(null)
                setMisJwt(null)
                setConnected(false)
                setErrorText(message)
            } finally {
                setBusy(false)
            }
        }
    }

    fun connectToPod() {
        if (isBusy) return
        val identity = currentIdentity ?: return
        val podForCertificate = selectedPod.ifBlank { defaultPod }
        if (podForCertificate.isBlank()) {
            setErrorText("No pod selected")
            return
        }
        setBusy(true)
        appScope.launch {
            try {
                val cert = createCertificateFor(identity.gid, podForCertificate)
                val misJwtService = ClientInjector.get<MisJwtService>()
                val jwt = misJwtService.buildMisJwt(identity, cert)
                setMisJwt(jwt)
                setConnected(true)
                setErrorText(null)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Pod connection failed", error)
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                setErrorText(message)
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

    if (isConnected && misJwt != null) {
        div {
            style = json("textAlign" to "right").unsafeCast<CSSProperties>()
            small { +identityText }
        }
        div {
            style = json("textAlign" to "center").unsafeCast<CSSProperties>()
            pre { +misJwt }
        }
    } else {
        h1 { +identityText }
        if (errorText != null) {
            p { +errorText }
        }
        select {
            value = selectedPod
            disabled = pods.isEmpty()
            onChange = { event ->
                val nextValue = (event.target.asDynamic().value as? String).orEmpty()
                setSelectedPod(nextValue)
            }
            if (pods.isEmpty()) {
                option {
                    value = ""
                    +"No pod configured"
                }
            } else {
                pods.forEach { pod ->
                    option {
                        value = pod
                        +pod
                    }
                }
            }
        }
        button {
            disabled = isBusy || currentIdentity == null || selectedPod.ifBlank { defaultPod }.isBlank()
            onClick = { connectToPod() }
            +"Connect"
        }
        button {
            disabled = isBusy
            onClick = { refreshIdentity(true) }
            +"Generate new identity"
        }
    }
}

private object ClientInjector : KoinComponent

private fun identityName(identity: Identity): String {
    val nameGenerator = ClientInjector.get<FictionalNameGenerator>()
    val gidBytes = Base64.fromBase64Url(identity.gid)
    val bits = BitStream(gidBytes)
    return nameGenerator.generate(bits).toString()
}

private suspend fun createCertificateFor(gid: String, podHost: String): PodCertificate {
    val podAccess = ClientInjector.podAccess(podHost)
    return podAccess.approveGid(gid)
}
