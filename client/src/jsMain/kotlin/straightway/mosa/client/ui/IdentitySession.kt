package straightway.mosa.client.ui

import kotlinx.browser.document
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
import straightway.mosa.client.identity.CryptoExport
import straightway.mosa.client.identity.IdentityRepository
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.pod.podAccess
import straightway.mosa.client.util.Base64
import straightway.mosa.client.util.Base64.toBase64Url
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator
import straightway.mosa.util.BitStream
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.HTMLAnchorElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

sealed interface IdentityScreen {
    data class Landing(
        val identityNameParts: FictionalName,
        val identityAvatar: String?,
        val isBusy: Boolean,
        val errorText: String?,
        val canDelete: Boolean,
        val canNavigatePrevious: Boolean,
        val canNavigateNext: Boolean,
        val isShowingLastIdentity: Boolean,
        val identityIndex: Int,
        val identityTotal: Int,
        val canConnect: Boolean,
        val onConnect: () -> Unit,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
        val onDelete: () -> Unit,
        val onSelect: (Int) -> Unit,
        val canDownload: Boolean,
        val onDownload: () -> Unit,
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
    val (identityNameParts, setIdentityNameParts) = useState(placeholderName(loadingText))
    val (identityAvatar, setIdentityAvatar) = useState<String?>(null)
    val (currentSelection, setCurrentSelection) = useState<IdentitySelection?>(null)
    val (misJwt, setMisJwt) = useState<String?>(null)
    val (isConnected, setConnected) = useState(false)
    val (isBusy, setBusy) = useState(false)
    val (errorText, setErrorText) = useState<String?>(null)
    val (queryPreview, setQueryPreview) = useState<String?>(null)
    val effectRan = useRef(false)
    val defaultPod = (window.asDynamic().MOSA_POD_BASE as? String).orEmpty()

    fun applySelection(selection: IdentitySelection) {
        IdentityVisuals(selection.identity).apply {
            setIdentityNameParts(name)
            setIdentityAvatar(avatar)
        }
        setCurrentSelection(selection)
        setMisJwt(null)
        setConnected(false)
        setQueryPreview(null)
        setErrorText(null)
    }

    fun refreshIdentity() {
        if (isBusy) return
        setBusy(true)
        setIdentityAvatar(null)
        sessionScope.launch {
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val selection = repository.loadOrCreate()
                applySelection(selection)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity refresh failed", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: unknownError
                val failureText = translator.translate("identity.failedToPrepare", mapOf("message" to message))
                setCurrentSelection(null)
                setIdentityAvatar(null)
                setMisJwt(null)
                setConnected(false)
                setQueryPreview(null)
                setIdentityNameParts(placeholderName(failureText))
                setErrorText(message)
            } finally {
                setBusy(false)
            }
        }
    }

    fun performNavigation(loader: suspend IdentityRepository.() -> IdentitySelection?) {
        if (isBusy) return
        setBusy(true)
        sessionScope.launch {
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val selection = loader(repository)
                if (selection != null) {
                    applySelection(selection)
                }
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity navigation failed", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: unknownError
                setErrorText(message)
            } finally {
                setBusy(false)
            }
        }
    }

    fun navigateToPrevious() {
        performNavigation { movePrevious() }
    }

    fun navigateToNext() {
        performNavigation { moveNext() }
    }

    fun deleteIdentity() {
        if (isBusy) return
        setBusy(true)
        sessionScope.launch {
            try {
                val repository = ClientInjector.get<IdentityRepository>()
                val selection = repository.deleteCurrent()
                applySelection(selection)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity removal failed", error)
                val message = error.message?.takeIf { it.isNotBlank() }
                    ?: error::class.simpleName
                    ?: unknownError
                setErrorText(message)
            } finally {
                setBusy(false)
            }
        }
    }

    fun navigateToIndex(index: Int) {
        performNavigation { select(index) }
    }

    fun connectToPod() {
        if (isBusy) return
        val identity = currentSelection?.identity ?: return
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

    suspend fun downloadKeyPair(
        identity: Identity,
        nameParts: FictionalName
    ) {
        val payload = identity.toIdentityKeyPairExport()
        val json = downloadJsonCodec.encodeToString(IdentityKeyPairExport.serializer(), payload)
        val blob = Blob(arrayOf(json), BlobPropertyBag(type = "application/json"))
        val anchor = document.createElement("a") as HTMLAnchorElement
        val fileName = nameParts.toIdentityDownloadFileName() + ".json"
        val url = (window.asDynamic().URL).createObjectURL(blob) as String
        anchor.href = url
        anchor.download = fileName
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.remove()
        (window.asDynamic().URL).revokeObjectURL(url)
    }

    fun downloadKeyPair() {
        val identity = currentSelection?.identity ?: return
        val nameParts = identityNameParts
        sessionScope.launch {
            try {
                downloadKeyPair(identity, nameParts)
            } catch (error: Throwable) {
                val console = window.asDynamic().console
                console?.error?.call(console, "Identity download failed", error)
            }
        }
    }

    useEffect(emptyArray<Any>()) {
        if (effectRan.current == true) return@useEffect
        effectRan.current = true
        refreshIdentity()
    }

    val canConnect = currentSelection != null && defaultPod.isNotBlank()
    val displayError = if (defaultPod.isBlank()) {
        errorText ?: missingPodMessage
    } else {
        errorText
    }
    val isShowingLastIdentity = currentSelection?.let { !it.hasNext } ?: false

    return if (isConnected && misJwt != null) {
        IdentityScreen.Connected(
            identityName = identityNameParts.toString(),
            misJwt = misJwt,
            queryPreview = queryPreview,
        )
    } else {
        IdentityScreen.Landing(
            identityNameParts = identityNameParts,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = displayError,
            canDelete = currentSelection != null,
            canNavigatePrevious = currentSelection?.hasPrevious ?: false,
            canNavigateNext = currentSelection != null,
            isShowingLastIdentity = isShowingLastIdentity,
            identityIndex = currentSelection?.index ?: 0,
            identityTotal = currentSelection?.total ?: 1,
            canConnect = canConnect,
            onConnect = { connectToPod() },
            onPrevious = { navigateToPrevious() },
            onNext = { navigateToNext() },
            onDelete = { deleteIdentity() },
            onSelect = { index -> navigateToIndex(index) },
            canDownload = currentSelection != null,
            onDownload = { downloadKeyPair() },
        )
    }
}

private suspend fun Identity.toIdentityKeyPairExport(): IdentityKeyPairExport {
    val export = ClientInjector.get<CryptoExport>()
    val publicKey = export.pubRawOf(keyPair).toBase64Url()
    val privateKey = export.privPkcs8Of(keyPair).toBase64Url()
    val payload = IdentityKeyPairExport(gid, publicKey, privateKey)
    return payload
}

private val sessionScope = MainScope()

private object ClientInjector : KoinComponent

private class IdentityVisuals(identity: Identity) {
    val name: FictionalName
    val avatar: String

    init {
        val nameGenerator = ClientInjector.get<FictionalNameGenerator>()
        val gidBytes = Base64.fromBase64Url(identity.gid)
        val bits = BitStream(gidBytes)
        name = nameGenerator.generate(bits)

        val avatarSeed = bits.nextBytes(8).toBase64Url()
        avatar = ClientInjector.get<DiceBearAvatarGenerator>().dataUri(avatarSeed)
    }
}

private fun placeholderName(text: String): FictionalName {
    val normalized = text.trim().ifBlank { "Identity" }
    return FictionalName(givenNames = listOf(normalized), sureName = "")
}

internal fun FictionalName.toIdentityDownloadFileName(): String {
    val tokens = buildList {
        addAll(givenNames)
        addAll(addendum?.split(Regex("[\\s-]+")).orEmpty())
        addAll(sureName.split(Regex("\\s+")))
    }.map { token ->
        token.filter { it.isLetterOrDigit() }
    }.filter { it.isNotEmpty() }
    val camel = tokens.joinToString("") { token ->
        if (token.all { it.isUpperCase() || it.isDigit() }) {
            token
        } else {
            val lower = token.lowercase()
            lower.replaceFirstChar { it.uppercase() }
        }
    }
    return camel.ifEmpty { "IdentityKeys" }
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

@Serializable
private data class IdentityKeyPairExport(
    val gid: String,
    val publicKey: String,
    val privateKey: String,
)

private val downloadJsonCodec = Json { prettyPrint = true }
