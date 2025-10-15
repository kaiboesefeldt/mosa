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
import react.dom.html.ReactHTML.h1
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
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
    val (identityText, setIdentityText) = useState("...")
    val (isBusy, setBusy) = useState(false)
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
                val misJwtService = ClientInjector.get<MisJwtService>()
                val identity =
                    if (regenerate) repository.regenerate()
                    else repository.loadOrCreate()

                val resolvedName = identityName(identity)
                setIdentityText(resolvedName)
                val podForCertificate = selectedPod.ifBlank { defaultPod }
                val cert = createCertificateFor(identity.gid, podForCertificate)
                misJwtService.buildMisJwt(identity, cert)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity refresh failed", error)
                val message = error.message ?: error::class.simpleName ?: "Unknown error"
                setIdentityText("Failed to prepare identity: $message")
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
    h1 { +identityText }
    button {
        disabled = isBusy
        onClick = { refreshIdentity(true) }
        +"Generate new identity"
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
