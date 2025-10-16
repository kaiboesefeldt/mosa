package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
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

sealed interface IdentityScreen {
    data class Landing(
        val identityName: String,
        val pods: List<String>,
        val selectedPod: String,
        val isBusy: Boolean,
        val errorText: String?,
        val canConnect: Boolean,
        val onSelectPod: (String) -> Unit,
        val onConnect: () -> Unit,
        val onGenerateNew: () -> Unit,
    ) : IdentityScreen

    data class Connected(
        val identityName: String,
        val misJwt: String,
    ) : IdentityScreen
}

fun useIdentitySession(): IdentityScreen {
    val (identityText, setIdentityText) = useState("...")
    val (currentIdentity, setCurrentIdentity) = useState<Identity?>(null)
    val (misJwt, setMisJwt) = useState<String?>(null)
    val (isConnected, setConnected) = useState(false)
    val (isBusy, setBusy) = useState(false)
    val (errorText, setErrorText) = useState<String?>(null)
    val effectRan = useRef(false)
    val defaultPod = (window.asDynamic().MOSA_POD_BASE as? String).orEmpty()
    val availablePods = listOfNotNull(defaultPod.takeIf { it.isNotBlank() })
    val (podSelection, setPodSelection) = useState(defaultPod)

    fun refreshIdentity(regenerate: Boolean) {
        if (isBusy) return
        setBusy(true)
        sessionScope.launch {
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
        val podForCertificate = podSelection.ifBlank { defaultPod }
        if (podForCertificate.isBlank()) {
            setErrorText("No pod selected")
            return
        }
        setBusy(true)
        sessionScope.launch {
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

    val canConnect = currentIdentity != null && podSelection.ifBlank { defaultPod }.isNotBlank()

    return if (isConnected && misJwt != null) {
        IdentityScreen.Connected(
            identityName = identityText,
            misJwt = misJwt,
        )
    } else {
        IdentityScreen.Landing(
            identityName = identityText,
            pods = availablePods,
            selectedPod = podSelection,
            isBusy = isBusy,
            errorText = errorText,
            canConnect = canConnect,
            onSelectPod = { nextValue ->
                setPodSelection(nextValue)
                setErrorText(null)
            },
            onConnect = { connectToPod() },
            onGenerateNew = { refreshIdentity(true) },
        )
    }
}

private val sessionScope = MainScope()

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
