package straightway.mosa.client.identity

import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import org.w3c.files.FileReader
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlin.js.unsafeCast
import straightway.mosa.client.browser.ObjectUrlFactory
import web.clipboard.writeText
import web.navigator.Navigator
import web.navigator.navigator

interface IdentityTransferPort {
    suspend fun download(identity: Identity, fileBaseName: String)
    suspend fun upload(): Identity?
    suspend fun copyToClipboard(identity: Identity)
}

class IdentityTransferService(
    private val codec: IdentityTransferCodec,
    private val urlFactory: ObjectUrlFactory,
) : IdentityTransferPort {
    override suspend fun copyToClipboard(identity: Identity) {
        val clipboard = navigatorOrNull()?.clipboard ?: error("Clipboard not available")
        val json = codec.encode(identity)
        clipboard.writeText(json).unsafeCast<Promise<Unit>>().await()
    }

    override suspend fun download(identity: Identity, fileBaseName: String) {
        val json = codec.encode(identity)
        val blob = Blob(arrayOf(json), BlobPropertyBag(type = "application/json"))
        val anchor = document.createElement("a") as HTMLAnchorElement
        val objectUrl = urlFactory.create(blob)
        anchor.href = objectUrl.value
        anchor.download = "$fileBaseName.json"
        document.body?.appendChild(anchor)
        try {
            anchor.click()
        } finally {
            anchor.remove()
            objectUrl.revoke()
        }
    }

    override suspend fun upload(): Identity? {
        val file = selectFile() ?: return null
        val content = readFile(file)
        return codec.decode(content)
    }

    private suspend fun selectFile(): File? =
        suspendCancellableCoroutine { continuation ->
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "application/json"
            input.style.display = "none"
            val cleanup: () -> Unit = {
                input.onchange = null
                input.oncancel = null
                input.remove()
            }
            input.onchange = { event: Event ->
                cleanup()
                val files = (event.currentTarget as? HTMLInputElement)?.files
                    ?: input.files
                val file = files?.item(0)
                continuation.resume(file)
            }
            input.oncancel = {
                cleanup()
                continuation.resume(null)
            }
            val body = document.body ?: error("Document body not available")
            body.appendChild(input)
            input.click()
            continuation.invokeOnCancellation { cleanup() }
        }

    private suspend fun readFile(file: File): String =
        suspendCancellableCoroutine { continuation ->
            val reader = FileReader()
            val cleanup: () -> Unit = {
                reader.onload = null
                reader.onerror = null
                reader.onabort = null
            }
            reader.onload = {
                cleanup()
                val result = reader.result as? String
                    ?: error("Failed to read file")
                continuation.resume(result)
            }
            reader.onerror = {
                cleanup()
                continuation.resumeWithException(reader.error ?: RuntimeException("File read failed"))
            }
            reader.onabort = {
                cleanup()
                continuation.resumeWithException(CancellationException("File read aborted"))
            }
            reader.readAsText(file)
        }

    private companion object {
        @Suppress("UnsafeCastFromDynamic")
        private fun navigatorOrNull(): Navigator? =
            if (js("typeof navigator === 'undefined'") as Boolean) {
                null
            } else {
                navigator
            }
    }
}
