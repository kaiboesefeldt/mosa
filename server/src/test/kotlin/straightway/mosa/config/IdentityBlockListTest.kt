package straightway.mosa.config

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityBlockListTest {
    @Test
    fun `parses yaml into blocked gids`() {
        val first = "gid-1".toBase64Url()
        val second = "gid-2".toBase64Url()
        val yaml =
            """
            blockedGids:
              - "$first"
              - "$second"
            """.trimIndent()

        val result = IdentityBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf(first, second), result.blockedGids)
    }

    @Test
    fun `returns empty list for empty yaml`() {
        val result = IdentityBlockList.transformer.transform(" \n".encodeToByteArray())

        assertEquals(emptyList(), result.blockedGids)
    }

    @Test
    fun `trims whitespace around entries`() {
        val gid = "gid".toBase64Url()
        val yaml =
            """
            blockedGids:
              - "  $gid  "
            """.trimIndent()

        val result = IdentityBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf(gid), result.blockedGids)
    }

    @Test
    fun `ignores blank entry`() {
        val yaml =
            """
            blockedGids:
              - "   "
              - non-blank
            """.trimIndent()

        val result = IdentityBlockList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf("non-blank"), result.blockedGids)
    }

    @Test
    fun `contains gid when blocked`() {
        val gid = "gid".toBase64Url()
        val blockList = IdentityBlockList(listOf(gid))

        assertEquals(true, gid in blockList)
    }

    @Test
    fun `does not contain gid when not blocked`() {
        val gid = "gid".toBase64Url()
        val blockList = IdentityBlockList(listOf("other".toBase64Url()))

        assertEquals(false, gid in blockList)
    }

    private fun String.toBase64Url(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodeToByteArray())
}
