package straightway.mosa.client.identity

import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class IdentityRepositoryTest {

    private lateinit var subtle: SubtleCrypto
    private lateinit var export: CryptoExport
    private lateinit var generator: IdentityGenerator
    private lateinit var protector: IdentityProtector

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
    fun `load or create keeps generated identity transient`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator, protector)

        val selection = repository.loadOrCreate()

        assertTrue(selection.identity.protectedExport == null)
        assertTrue(store.storedStates.isEmpty())
        assertTrue(selection.isPersisted.not())
    }

    @Test
    fun `protectCurrent persists identity and marks it persisted`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator, protector)
        repository.loadOrCreate()

        val selection = repository.protectCurrent("password")

        assertTrue(selection.isPersisted)
        val storedState = assertNotNull(store.state)
        assertTrue(storedState.current().protectedExport != null)
    }

    @Test
    fun `import persists protected identity`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator, protector)
        val protected = protector.protect(generator.generate(), "secret")

        val selection = repository.import(protected)

        assertTrue(selection.isPersisted)
        val stored = assertNotNull(store.state)
        assertEquals(protected.gid, stored.current().gid)
    }

    @Test
    fun `persist fails when identity is not protected`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator, protector)
        repository.loadOrCreate()

        assertFails { repository.persist() }
    }

    @Test
    fun `delete removes persisted identity from store`() = runTest {
        val persisted = protector.protect(generator.generate(), "secret")
        val store = RecordingIdentityStore(IdentityStoreState(listOf(persisted), 0))
        val repository = IdentityRepository(store, generator, protector)

        val selection = repository.deleteCurrent()

        assertTrue(selection.isPersisted.not())
        assertTrue(store.wasCleared)
        assertTrue(store.storedStates.isEmpty())
    }
}

private class RecordingIdentityStore(initialState: IdentityStoreState?) : IdentityStore {
    var state: IdentityStoreState? = initialState
    val storedStates = mutableListOf<IdentityStoreState>()
    var wasCleared: Boolean = false

    override suspend fun loadState(): IdentityStoreState? = state

    override suspend fun storeState(state: IdentityStoreState) {
        storedStates += state
        this.state = state
    }

    override suspend fun clearState() {
        wasCleared = true
        state = null
    }
}
