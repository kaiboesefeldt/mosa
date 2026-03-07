package straightway.mosa.client.content

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import straightway.mosa.client.browser.ObjectUrlFactory

open class PostsCsvDownloader(
    private val urlFactory: ObjectUrlFactory,
    private val anchorFactory: () -> HTMLAnchorElement = {
        document.createElement("a") as HTMLAnchorElement
    },
    private val bodyProvider: () -> HTMLElement? = {
        document.body
    },
) {
    open suspend fun download(
        content: ByteArray,
        fileBaseName: String,
    ) {
        val blob = Blob(arrayOf(content.decodeToString()), BlobPropertyBag(type = "text/csv;charset=utf-8"))
        val anchor = anchorFactory()
        val objectUrl = urlFactory.create(blob)
        anchor.href = objectUrl.value
        anchor.download = "$fileBaseName.csv"
        bodyProvider()?.appendChild(anchor)
        try {
            anchor.click()
        } finally {
            anchor.remove()
            objectUrl.revoke()
        }
    }
}
