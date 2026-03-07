package straightway.mosa.client.content

import kotlin.js.js
import kotlin.js.Promise
import kotlin.js.unsafeCast
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLElement
import org.w3c.files.Blob
import straightway.mosa.client.browser.ObjectUrlFactory
import straightway.mosa.client.browser.ObjectUrlHandle

class PostsCsvDownloaderTest {
    @Test
    fun `creates csv blob and triggers browser download`() = runTest {
        val createdBlobs = mutableListOf<Blob>()
        val revokedUrls = mutableListOf<String>()
        val appendedAnchors = mutableListOf<HTMLAnchorElement>()
        val clickedHrefs = mutableListOf<String>()
        val clickedDownloads = mutableListOf<String>()
        val removedAnchors = mutableListOf<HTMLAnchorElement>()

        val anchor =
            js(
                """({
                    href: "",
                    download: "",
                    click: function () {},
                    remove: function () {}
                })""",
            ).unsafeCast<HTMLAnchorElement>()
        anchor.asDynamic().click = {
            clickedHrefs += anchor.href
            clickedDownloads += anchor.download
        }
        anchor.asDynamic().remove = {
            removedAnchors += anchor
        }

        val body =
            js(
                """({
                    appendChild: function () {}
                })""",
            ).unsafeCast<HTMLElement>()
        body.asDynamic().appendChild = { child: dynamic ->
            appendedAnchors += child.unsafeCast<HTMLAnchorElement>()
        }

        val downloader =
            PostsCsvDownloader(
                urlFactory =
                    object : ObjectUrlFactory {
                        override fun create(blob: Blob): ObjectUrlHandle {
                            createdBlobs += blob
                            return ObjectUrlHandle("blob:test") { revokedUrls += it }
                        }
                    },
                anchorFactory = { anchor },
                bodyProvider = { body },
            )

        downloader.download("id,text\n1,Hello".encodeToByteArray(), "MosaExport")

        assertEquals(listOf(anchor), appendedAnchors)
        assertEquals(listOf("blob:test"), clickedHrefs)
        assertEquals(listOf("MosaExport.csv"), clickedDownloads)
        assertEquals(listOf(anchor), removedAnchors)
        assertEquals(listOf("blob:test"), revokedUrls)
        assertEquals("text/csv;charset=utf-8", createdBlobs.single().type)
        val content = blobText(createdBlobs.single())
        assertEquals("id,text\n1,Hello", content)
    }

    @Test
    fun `revokes object url when click fails`() = runTest {
        val revokedUrls = mutableListOf<String>()
        val anchor =
            js(
                """({
                    href: "",
                    download: "",
                    click: function () {},
                    remove: function () {}
                })""",
            ).unsafeCast<HTMLAnchorElement>()
        var removed = false
        anchor.asDynamic().click = {
            throw IllegalStateException("boom")
        }
        anchor.asDynamic().remove = {
            removed = true
        }

        val downloader =
            PostsCsvDownloader(
                urlFactory =
                    object : ObjectUrlFactory {
                        override fun create(blob: Blob): ObjectUrlHandle =
                            ObjectUrlHandle("blob:test") { revokedUrls += it }
                    },
                anchorFactory = { anchor },
                bodyProvider = { null },
            )

        val error =
            runCatching {
                downloader.download("x".encodeToByteArray(), "Export")
            }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(removed)
        assertEquals(listOf("blob:test"), revokedUrls)
    }
}

private suspend fun blobText(blob: Blob): String =
    blob.asDynamic().text().unsafeCast<Promise<String>>().await()
