package straightway.mosa.client.identity.privacy

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.File
import org.w3c.files.FileReader
import straightway.mosa.client.browser.ObjectUrlFactory
import straightway.mosa.client.util.toByteArray
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PrivacySnapshotTransferService(
    private val urlFactory: ObjectUrlFactory,
    private val anchorFactory: () -> HTMLAnchorElement = {
        document.createElement("a") as HTMLAnchorElement
    },
    private val inputFactory: () -> HTMLInputElement = {
        document.createElement("input") as HTMLInputElement
    },
    private val bodyProvider: () -> HTMLElement? = {
        document.body
    },
) : PrivacySnapshotTransferPort {
    override suspend fun download(
        snapshot: ByteArray,
        fileBaseName: String,
    ) {
        val blob = Blob(arrayOf(snapshot), BlobPropertyBag(type = ContentType))
        val anchor = anchorFactory()
        val objectUrl = urlFactory.create(blob)
        anchor.href = objectUrl.value
        anchor.download = "$fileBaseName.bin"
        bodyProvider()?.appendChild(anchor)
        try {
            anchor.click()
        } finally {
            anchor.remove()
            objectUrl.revoke()
        }
    }

    override suspend fun upload(): ByteArray? {
        val file = selectFile() ?: return null
        return readFile(file)
    }

    private suspend fun selectFile(): File? =
        suspendCancellableCoroutine { continuation ->
            val input = inputFactory()
            input.type = "file"
            input.accept = ".bin,$ContentType"
            input.style.display = "none"
            val cleanup: () -> Unit = {
                input.onchange = null
                input.oncancel = null
                input.remove()
            }
            input.onchange = { event: Event ->
                cleanup()
                val files = (event.currentTarget as? HTMLInputElement)?.files ?: input.files
                continuation.resume(files?.item(0))
            }
            input.oncancel = {
                cleanup()
                continuation.resume(null)
            }
            val body = bodyProvider() ?: error("Document body not available")
            body.appendChild(input)
            input.click()
            continuation.invokeOnCancellation { cleanup() }
        }

    private suspend fun readFile(file: File): ByteArray =
        suspendCancellableCoroutine { continuation ->
            val reader = FileReader()
            val cleanup: () -> Unit = {
                reader.onload = null
                reader.onerror = null
                reader.onabort = null
            }
            reader.onload = {
                cleanup()
                val result = reader.result as? ArrayBuffer ?: error("Failed to read file")
                continuation.resume(result.toByteArray())
            }
            reader.onerror = {
                cleanup()
                continuation.resumeWithException(reader.error ?: RuntimeException("File read failed"))
            }
            reader.onabort = {
                cleanup()
                continuation.resumeWithException(CancellationException("File read aborted"))
            }
            reader.readAsArrayBuffer(file)
        }

    private companion object {
        private const val ContentType = "application/octet-stream"
    }
}
