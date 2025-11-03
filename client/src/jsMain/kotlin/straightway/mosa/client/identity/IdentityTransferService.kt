package straightway.mosa.client.identity

import kotlinx.browser.document
import kotlinx.browser.window
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

class IdentityTransferService(
    private val codec: IdentityTransferCodec,
) {
    suspend fun download(identity: Identity, fileBaseName: String) {
        val json = codec.encode(identity)
        val blob = Blob(arrayOf(json), BlobPropertyBag(type = "application/json"))
        val anchor = document.createElement("a") as HTMLAnchorElement
        val urlCreator = window.asDynamic().URL ?: error("URL API not available")
        val url = urlCreator.createObjectURL(blob) as String
        anchor.href = url
        anchor.download = "$fileBaseName.json"
        document.body?.appendChild(anchor)
        anchor.click()
        anchor.remove()
        urlCreator.revokeObjectURL(url)
    }

    suspend fun upload(): Identity? {
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
}
