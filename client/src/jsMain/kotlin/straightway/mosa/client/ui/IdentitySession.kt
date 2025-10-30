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
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.pod.podAccess
import straightway.mosa.client.util.Base64
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.util.BitStream
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult

sealed interface IdentityScreen {
    data class Landing(
        val identityName: String,
        val identityAvatar: String?,
        val isBusy: Boolean,
        val errorText: String?,
        val canConnect: Boolean,
        val onConnect: () -> Unit,
        val onGenerateNew: () -> Unit,
    ) : IdentityScreen

    data class Connected(
        val identityName: String,
        val misJwt: String,
        val queryPreview: String?,
    ) : IdentityScreen
}

fun useIdentitySession(): IdentityScreen {
    val translator = ClientInjector.get<Translator>()
    val loadingText = translator.translate("identity.loading")
    val unknownError = translator.translate("identity.unknownError")
    val missingPodMessage = translator.translate("landing.noPodConfigured")
    val (identityText, setIdentityText) = useState(loadingText)
    val (identityAvatar, setIdentityAvatar) = useState<String?>(null)
    val (currentIdentity, setCurrentIdentity) = useState<Identity?>(null)
    val (misJwt, setMisJwt) = useState<String?>(null)
    val (isConnected, setConnected) = useState(false)
    val (isBusy, setBusy) = useState(false)
    val (errorText, setErrorText) = useState<String?>(null)
    val (queryPreview, setQueryPreview) = useState<String?>(null)
    val effectRan = useRef(false)
    val defaultPod = (window.asDynamic().MOSA_POD_BASE as? String).orEmpty()
    val avatarGenerator = ClientInjector.get<DiceBearAvatarGenerator>()

    fun refreshIdentity(regenerate: Boolean) {
        if (isBusy) return
        setBusy(true)
        setIdentityAvatar(null)
        sessionScope.launch {
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val identity =
                    if (regenerate) repository.regenerate()
                    else repository.loadOrCreate()
                val resolvedName = identityName(identity)
                setIdentityText(resolvedName)
                setCurrentIdentity(identity)
                setIdentityAvatar(avatarGenerator.dataUri(identity.gid))
                setMisJwt(null)
                setConnected(false)
                setQueryPreview(null)
                setErrorText(null)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity refresh failed", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: unknownError
                val failureText = translator.translate("identity.failedToPrepare", mapOf("message" to message))
                setIdentityText(failureText)
                setCurrentIdentity(null)
                setIdentityAvatar(null)
                setMisJwt(null)
                setConnected(false)
                setQueryPreview(null)
                setErrorText(message)
            } finally {
                setBusy(false)
            }
        }
    }

    fun connectToPod() {
        if (isBusy) return
        val identity = currentIdentity ?: return
        val podForCertificate = defaultPod
        if (podForCertificate.isBlank()) {
            setErrorText(missingPodMessage)
            return
        }
        setBusy(true)
        setQueryPreview(null)
        sessionScope.launch {
            try {
                val cert = createCertificateFor(identity.gid, podForCertificate)
                val misJwtService = ClientInjector.get<MisJwtService>()
                val jwt = misJwtService.buildMisJwt(identity, cert)
                val previewTopic = "Anything"
                val preview =
                    runCatching {
                        fetchPrototypeQuery(podForCertificate, jwt, previewTopic)
                    }.map {
                        prototypePreviewOf(translator, previewTopic, it)
                    }.getOrElse { error ->
                        val console = window.asDynamic().console
                        console?.warn?.call(console, "Prototype query failed", error)
                        error.message?.takeIf { it.isNotBlank() }
                            ?: error::class.simpleName
                            ?: unknownError
                    }
                setMisJwt(jwt)
                setQueryPreview(preview)
                setConnected(true)
                setErrorText(null)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Pod connection failed", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: unknownError
                setQueryPreview(null)
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

    val canConnect = currentIdentity != null && defaultPod.isNotBlank()
    val displayError = if (defaultPod.isBlank()) {
        errorText ?: missingPodMessage
    } else {
        errorText
    }

    return if (isConnected && misJwt != null) {
        IdentityScreen.Connected(
            identityName = identityText,
            misJwt = misJwt,
            queryPreview = queryPreview,
        )
    } else {
        IdentityScreen.Landing(
            identityName = identityText,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = displayError,
            canConnect = canConnect,
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

private suspend fun fetchPrototypeQuery(podHost: String, jwt: String, topic: String): QueryResult {
    val podAccess = ClientInjector.podAccess(podHost)
    return podAccess.query(QueryRequest(topic = topic), jwt)
}

private fun prototypePreviewOf(translator: Translator, topic: String, result: QueryResult): String {
    if (result.entries.isEmpty()) {
        return translator.translate("preview.resultsEmpty", mapOf("topic" to topic))
    }
    val header = translator.translate("preview.resultsHeader", mapOf("topic" to topic))
    return buildString {
        appendLine(header)
        result.entries.forEach { entry ->
            append("- ")
            append(entry.content)
            append(" (")
            append(entry.similarity)
            appendLine(")")
        }
    }.trim()
}
