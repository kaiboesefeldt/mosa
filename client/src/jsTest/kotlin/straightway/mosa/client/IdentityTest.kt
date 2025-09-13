package straightway.mosa.client

import kotlinx.browser.window
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdentityTest {
    @Test
    fun `generate identity provides gid and keys`(): Promise<Unit> {
        val subtle = window.asDynamic().crypto?.subtle
        if (subtle == null) {
            // Skip gracefully if WebCrypto is not available
            return Promise.resolve(Unit)
        }
        return IdentityGenerator()
            .generate()
            .then { id ->
                // Ed25519 raw public key has 32 bytes
                assertEquals(32, id.pub.length)
                // GID is base64url of sha256(pubRaw) -> 43 chars (no padding)
                assertEquals(43, id.gid.length)
                // Private PKCS#8 must be non-empty
                assertTrue(id.priv.length > 0)
            }
    }
}
