package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.fail

class IndexedDbIdentityStoreTest {

    private val components = createComponents()

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `store and load roundtrip preserves gid and key material`() = runTestWithIdentities {
        val identity = it.first()
        components.store.store(identity)

        val reloaded = assertNotNull(components.store.load())

        assertEquals(identity, reloaded)
    }

    @Test
    fun `load returns null when no identity is persisted`() = runTest {
        val loaded = components.store.load()
        assertNull(loaded)
    }

    @Test
    fun `storing a new identity overwrites previous entry`() = runTestWithIdentities {
        components.store.store(it.first())
        components.store.store(it.last())

        val reloaded = assertNotNull(components.store.load())

        assertEquals(it.last(), reloaded)
    }

    private fun runTestWithIdentities(block: suspend (List<Identity>) -> Unit) = runTest {
        val testIdentities = (1..2).map { components.generator.generate() }
        try {
            clearDatabase()
            block(testIdentities)
        } finally {
            clearDatabase()
        }
    }

    private suspend fun assertEquals(expected: Identity, actual: Identity) {
        assertEquals(expected.gid, actual.gid)
        assertContentEquals(
            components.export.pubRawOf(expected.keyPair),
            components.export.pubRawOf(actual.keyPair)
        )
        assertContentEquals(
            components.export.privPkcs8Of(expected.keyPair),
            components.export.privPkcs8Of(actual.keyPair)
        )
    }

    private val indexedDb: dynamic get() = window.asDynamic().indexedDB ?: fail("No indexedDB")

    private suspend fun clearDatabase() {
        val request = indexedDb.deleteDatabase(databaseName)
        suspendCancellableCoroutine { continuation ->
            request.onsuccess = { continuation.resume(Unit) }
            request.onerror = { continuation.resume(Unit) }
            request.onblocked = { continuation.resume(Unit) }
        }
    }

    private fun createComponents(): Components {
        val rawSubtle = window.asDynamic().crypto?.subtle ?: fail("Subtle crypto not found")
        val subtle = SubtleCrypto(rawSubtle)
        val export = CryptoExport(subtle)
        val store = IndexedDbIdentityStore(subtle, export, indexedDb)
        val generator = IdentityGenerator(export, subtle)
        return Components(subtle, export, store, generator)
    }

    private companion object {
        const val databaseName = "mosa-identity"
    }

    private data class Components(
        val subtle: SubtleCrypto,
        val export: CryptoExport,
        val store: IndexedDbIdentityStore,
        val generator: IdentityGenerator,
    )
}
