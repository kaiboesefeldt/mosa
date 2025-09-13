package straightway.mosa.client

import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityTest {
    @Test
    fun `generate identity provides gid and keys`() = runTest {
        val id = IdentityGenerator().generate()

        // Ed25519 raw public key has 32 bytes
        assertEquals(32, id.pub.length)
        // GID is base64url of sha256(pubRaw) -> 43 chars (no padding)
        assertEquals(43, id.gid.length)
        // Private PKCS#8 must be non-empty
        assertTrue(id.priv.length > 0)
    }
}
