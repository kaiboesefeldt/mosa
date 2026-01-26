package straightway.mosa.identity

import straightway.mosa.util.BitStream
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class IdentityLogEntryFormatterTest {
    @Test
    fun `formats gid with fictional name when base64 url`() {
        val gidBytes = "gid-1".encodeToByteArray()
        val gid = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(gidBytes)
        val expectedName = FictionalNameGenerator().generate(BitStream(gidBytes)).toString()

        val result = IdentityLogEntryFormatter.format(gid)

        assertEquals("$gid ($expectedName)", result)
    }

    @Test
    fun `returns gid when not base64 url`() {
        val gid = "not-base64"

        val result = IdentityLogEntryFormatter.format(gid)

        assertEquals(gid, result)
    }
}
