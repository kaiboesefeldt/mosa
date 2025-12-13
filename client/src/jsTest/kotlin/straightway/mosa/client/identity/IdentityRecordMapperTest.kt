package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.js.json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class IdentityRecordMapperTest {

    private lateinit var mapper: IdentityRecordMapper
    private lateinit var generator: IdentityGenerator
    private lateinit var export: CryptoExport
    private lateinit var protector: IdentityProtector
    private lateinit var subtle: SubtleCrypto

    @BeforeTest
    fun setup() {
        ensureStacktraceMapperInstalled()
        val rawSubtle = window.asDynamic().crypto?.subtle ?: fail("Subtle crypto not found")
        subtle = SubtleCrypto(rawSubtle)
        export = CryptoExport(subtle)
        generator = IdentityGenerator(export, subtle)
        protector = IdentityProtector(subtle, export)
        mapper = IdentityRecordMapper()
    }

    @Test
    fun `record roundtrip keeps identities`() = runTest {
        val identities = listOf(generator.generate(), generator.generate()).map { protector.protect(it, "pass") }
        val state = IdentityStoreState(identities, 1)

        val record = mapper.toRecord(state)
        val restored = assertNotNull(mapper.toState(record))

        assertEquals(state.activeIndex, restored.activeIndex)
        state.identities.indices.forEach { index ->
            assertIdentityEquals(state.identities[index], restored.identities[index])
        }
    }

    @Test
    fun `toState returns null when identities are missing`() = runTest {
        val record = json("activeIndex" to 0)

        val restored = mapper.toState(record)

        assertNull(restored)
    }

    @Test
    fun `toState clamps invalid active index`() = runTest {
        val state = IdentityStoreState(listOf(protector.protect(generator.generate(), "pass")), 0)
        val record = mapper.toRecord(state)
        record.activeIndex = 99

        val restored = assertNotNull(mapper.toState(record))

        assertEquals(0, restored.activeIndex)
    }

    @Test
    fun `toState fails when identity fields are incomplete`() = runTest {
        val record =
            json(
                "activeIndex" to 0,
                "identities" to arrayOf(
                    json(
                        "gid" to "gid",
                        "publicKey" to "deadbeef",
                    )
                ),
            )

        val restored = mapper.toState(record)

        assertNull(restored)
    }

    private suspend fun assertIdentityEquals(expected: Identity, actual: Identity) {
        assertEquals(expected.gid, actual.gid)
        assertEquals(expected.protectedExport, actual.protectedExport)
        val message = "message".encodeToByteArray()
        val signature = subtle.sign("Ed25519", expected.keyPair.privateKey, message)
        val verified = subtle.verify("Ed25519", actual.keyPair.publicKey, signature, message)
        assertTrue(verified)
    }
}
