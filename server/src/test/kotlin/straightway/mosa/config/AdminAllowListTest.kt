package straightway.mosa.config

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class AdminAllowListTest {
    @Test
    fun `parses yaml into admin gids`() {
        val first = "gid-1".toBase64Url()
        val second = "gid-2".toBase64Url()
        val yaml =
            """
            adminGids:
              - "$first"
              - "$second"
            """.trimIndent()

        val result = AdminAllowList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf(first, second), result.adminGids)
    }

    @Test
    fun `returns empty list for empty yaml`() {
        val result = AdminAllowList.transformer.transform(" \n".encodeToByteArray())

        assertEquals(emptyList(), result.adminGids)
    }

    @Test
    fun `trims whitespace around entries`() {
        val gid = "gid".toBase64Url()
        val yaml =
            """
            adminGids:
              - "  $gid  "
            """.trimIndent()

        val result = AdminAllowList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf(gid), result.adminGids)
    }

    @Test
    fun `ignores blank entry`() {
        val yaml =
            """
            adminGids:
              - "   "
              - non-blank
            """.trimIndent()

        val result = AdminAllowList.transformer.transform(yaml.encodeToByteArray())

        assertEquals(listOf("non-blank"), result.adminGids)
    }

    @Test
    fun `contains gid when admin`() {
        val gid = "gid".toBase64Url()
        val allowList = AdminAllowList(listOf(gid))

        assertEquals(true, gid in allowList)
    }

    @Test
    fun `does not contain gid when not admin`() {
        val gid = "gid".toBase64Url()
        val allowList = AdminAllowList(listOf("other".toBase64Url()))

        assertEquals(false, gid in allowList)
    }

    private fun String.toBase64Url(): String = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(encodeToByteArray())
}
