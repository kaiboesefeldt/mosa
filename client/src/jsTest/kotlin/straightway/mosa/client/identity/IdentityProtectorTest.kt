package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.test.assertFails

class IdentityProtectorTest {

    private lateinit var protector: IdentityProtector
    private lateinit var generator: IdentityGenerator
    private lateinit var export: CryptoExport
    private lateinit var subtle: SubtleCrypto

    @BeforeTest
    fun setup() {
        ensureStacktraceMapperInstalled()
        val rawSubtle = window.asDynamic().crypto?.subtle ?: fail("Subtle crypto not found")
        subtle = SubtleCrypto(rawSubtle)
        export = CryptoExport(subtle)
        generator = IdentityGenerator(export, subtle)
        protector = IdentityProtector(subtle, export)
    }

    @Test
    fun `protects identity and refuses to export private key afterward`() = runTest {
        val identity = generator.generate()

        val protected = protector.protect(identity, "password")

        assertNotNull(protected.protectedExport)
        assertFails { export.privPkcs8Of(protected.keyPair) }
    }

    @Test
    fun `imports protected identity with password and keeps gid`() = runTest {
        val identity = generator.generate()
        val protected = protector.protect(identity, "password")
        val serialized = protected.protectedExport ?: fail("Missing protected export")

        val restored = protector.import(serialized, "password")

        assertEquals(identity.gid, restored.gid)
        val message = "message".encodeToByteArray()
        val signature = subtle.sign("Ed25519", restored.keyPair.privateKey, message)
        val verified = subtle.verify("Ed25519", restored.keyPair.publicKey, signature, message)
        assertTrue(verified)
    }

    @Test
    fun `import fails with wrong password`() = runTest {
        val identity = generator.generate()
        val protected = protector.protect(identity, "password")
        val serialized = protected.protectedExport ?: fail("Missing protected export")

        assertFails { protector.import(serialized, "invalid") }
    }
}
