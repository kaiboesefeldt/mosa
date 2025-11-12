package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.coroutines.resume
import kotlin.js.asDynamic
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import web.idb.IDBFactory
import web.idb.IDBOpenDBRequest
import web.idb.indexedDB

class IndexedDbIdentityStoreTest {

    private val components = createComponents()

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `store and load roundtrip preserves gid and key material`() = runTestWithIdentities {
        val identity = it.first()
        val state = IdentityStoreState(listOf(identity, it.last()), 1)
        components.store.storeState(state)

        val reloaded = assertNotNull(components.store.loadState())

        assertEquals(state.activeIndex, reloaded.activeIndex)
        state.identities.indices.forEach { index ->
            assertIdentityEquals(state.identities[index], reloaded.identities[index])
        }
    }

    @Test
    fun `load returns null when no identity is persisted`() = runTest {
        val loaded = components.store.loadState()
        assertNull(loaded)
    }

    @Test
    fun `storing a new identity overwrites previous entry`() = runTestWithIdentities {
        val first = IdentityStoreState(listOf(it.first()), 0)
        val second = IdentityStoreState(listOf(it.last()), 0)
        components.store.storeState(first)
        components.store.storeState(second)

        val reloaded = assertNotNull(components.store.loadState())

        assertEquals(second.activeIndex, reloaded.activeIndex)
        assertEquals(1, reloaded.identities.size)
        assertIdentityEquals(it.last(), reloaded.identities.single())
    }

    @Test
    fun `store gracefully handles missing indexeddb`() = runTestWithIdentities {
        val fallbackStore =
            IndexedDbIdentityStore(
                TestLogger,
                IndexedDbSchemaManager(null),
                IdentityRecordMapper(components.subtle, components.export),
                IndexedDbAsyncBridge(),
            )
        val state = IdentityStoreState(listOf(it.first()), 0)

        fallbackStore.storeState(state)

        val reloaded = fallbackStore.loadState()
        assertNull(reloaded)
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

    private suspend fun assertIdentityEquals(expected: Identity, actual: Identity) {
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

    private val indexedDb: IDBFactory
        get() = if ((js("typeof indexedDB === 'undefined'") as Boolean)) {
            fail("No indexedDB")
        } else {
            indexedDB
        }

    private suspend fun clearDatabase() {
        suspendOnOpenRequest(indexedDb.deleteDatabase(databaseName))
    }

    private fun createComponents(): Components {
        val rawSubtle = window.asDynamic().crypto?.subtle ?: fail("Subtle crypto not found")
        val subtle = SubtleCrypto(rawSubtle)
        val export = CryptoExport(subtle)
        val store =
            IndexedDbIdentityStore(
                TestLogger,
                IndexedDbSchemaManager(indexedDb),
                IdentityRecordMapper(subtle, export),
                IndexedDbAsyncBridge(),
            )
        val generator = IdentityGenerator(export, subtle)
        return Components(subtle, export, store, generator)
    }

    private companion object {
        const val databaseName = "mosa-identity"
    }

    private suspend fun suspendOnOpenRequest(request: IDBOpenDBRequest) {
        suspendCancellableCoroutine { continuation ->
            val dynamicRequest = request.asDynamic()
            val resume: () -> Unit = { continuation.resume(Unit) }
            dynamicRequest.onsuccess = resume
            dynamicRequest.onerror = resume
            dynamicRequest.onblocked = resume
        }
    }

    private data class Components(
        val subtle: SubtleCrypto,
        val export: CryptoExport,
        val store: IndexedDbIdentityStore,
        val generator: IdentityGenerator,
    )

    private object TestLogger : Logger {
        override fun error(message: String, throwable: Throwable?) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
    }
}
