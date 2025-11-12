package straightway.mosa.client.browser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.w3c.files.Blob

class WindowObjectUrlFactoryTest {
    @Test
    fun `creates handle through provided api`() {
        val createdBlobs = mutableListOf<Blob>()
        val revokedUrls = mutableListOf<String>()
        val api = object : UrlApi {
            override fun createObjectURL(blob: Blob): String {
                createdBlobs += blob
                return "blob:${createdBlobs.size}"
            }

            override fun revokeObjectURL(url: String) {
                revokedUrls += url
            }
        }
        val factory = WindowObjectUrlFactory { api }
        val blob = Blob(arrayOf("payload"))

        val handle = factory.create(blob)
        handle.revoke()

        assertEquals("blob:1", handle.value)
        assertSame(blob, createdBlobs.single())
        assertEquals(listOf("blob:1"), revokedUrls)
    }

    @Test
    fun `fails when api is unavailable`() {
        val factory = WindowObjectUrlFactory { null }
        val blob = Blob(arrayOf("payload"))

        assertFailsWith<IllegalStateException> {
            factory.create(blob)
        }
    }
}
