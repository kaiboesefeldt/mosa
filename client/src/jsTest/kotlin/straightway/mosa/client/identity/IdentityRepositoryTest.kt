package straightway.mosa.client.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import straightway.mosa.client.browser.CryptoKeyPair

class IdentityRepositoryTest {
    @Test
    fun `load or create keeps generated identity transient`() = runTest {
        val generated = identity("gid-transient")
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator = StaticIdentityGenerator(generated))

        val selection = repository.loadOrCreate()

        assertEquals(generated, selection.identity)
        assertTrue(store.storedStates.isEmpty())
        assertFalse(selection.isPersisted)
    }

    @Test
    fun `select moves to requested index and persists on demand`() = runTest {
        val first = identity("gid-1")
        val second = identity("gid-2")
        val third = identity("gid-3")
        val initialState = IdentityStoreState(listOf(first, second, third), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)

        val selection = repository.select(2)
        repository.persist()

        assertEquals(2, selection?.index)
        assertEquals(third, selection?.identity)
        assertEquals(1, store.storedStates.size)
        assertEquals(2, store.storedStates.single().activeIndex)
        assertTrue(selection?.isPersisted == true)
    }

    @Test
    fun `select ignores indexes outside stored range`() = runTest {
        val first = identity("gid-1")
        val second = identity("gid-2")
        val initialState = IdentityStoreState(listOf(first, second), activeIndex = 1)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)

        val selection = repository.select(5)
        repository.persist()

        assertEquals(1, selection?.index)
        assertEquals(second, selection?.identity)
        assertEquals(1, store.storedStates.size)
        assertEquals(1, store.storedStates.single().activeIndex)
        assertTrue(selection?.isPersisted == true)
    }

    @Test
    fun `import keeps identity transient until persisted`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)
        val uploaded = identity("gid-42")

        val selection = repository.import(uploaded)
        repository.persist()
        val persistedSelection = repository.loadOrCreate()

        val stored = store.state
        assertEquals(uploaded, selection.identity)
        assertEquals(0, selection.index)
        assertEquals(1, selection.total)
        assertEquals(1, store.storedStates.size)
        assertEquals(uploaded, stored?.current())
        assertFalse(selection.isPersisted)
        assertTrue(persistedSelection.isPersisted)
    }

    @Test
    fun `import selects existing identity when gid matches`() = runTest {
        val first = identity("gid-1")
        val second = identity("gid-2")
        val initialState = IdentityStoreState(listOf(first, second), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)
        val duplicate = identity("gid-2")

        val selection = repository.import(duplicate)
        repository.persist()
        val persistedSelection = repository.loadOrCreate()

        assertEquals(second, selection.identity)
        assertEquals(1, selection.index)
        assertEquals(2, selection.total)
        assertEquals(1, store.state?.activeIndex)
        assertTrue(store.storedStates.single().identities === store.state?.identities)
        assertTrue(selection.isPersisted)
        assertTrue(persistedSelection.isPersisted)
    }

    @Test
    fun `import appends identity when gid unknown`() = runTest {
        val first = identity("gid-1")
        val initialState = IdentityStoreState(listOf(first), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)
        val uploaded = identity("gid-99")

        val selection = repository.import(uploaded)
        repository.persist()
        val persistedSelection = repository.loadOrCreate()

        val stored = store.state
        assertEquals(uploaded, selection.identity)
        assertEquals(1, selection.index)
        assertEquals(2, selection.total)
        assertEquals(1, store.storedStates.size)
        assertEquals(2, stored?.identities?.size)
        assertEquals(uploaded, stored?.current())
        assertFalse(selection.isPersisted)
        assertTrue(persistedSelection.isPersisted)
    }

    @Test
    fun `persist skips transient identities that are not selected`() = runTest {
        val persisted = identity("gid-1")
        val generator =
            QueuedIdentityGenerator(
                listOf(
                    identity("gid-2"),
                    identity("gid-3"),
                )
            )
        val initialState = IdentityStoreState(listOf(persisted), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = generator)

        repository.moveNext()
        repository.moveNext()
        repository.select(1)
        repository.persist()

        assertEquals(1, store.storedStates.size)
        val storedGids = store.state?.identities?.map { it.gid }
        assertEquals(listOf("gid-1", "gid-2"), storedGids)
    }

    @Test
    fun `persist keeps transient identities in memory`() = runTest {
        val persisted = identity("gid-1")
        val generator =
            QueuedIdentityGenerator(
                listOf(
                    identity("gid-2"),
                    identity("gid-3"),
                )
            )
        val initialState = IdentityStoreState(listOf(persisted), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = generator)

        val first = repository.moveNext()
        val second = repository.moveNext()

        repository.persist()

        val selection = repository.select(1)

        assertEquals("gid-2", selection?.identity?.gid)
        val storedGids = store.state?.identities?.map { it.gid }
        assertEquals(listOf("gid-1", "gid-3"), storedGids)
        assertEquals("gid-2", first?.identity?.gid)
        assertEquals("gid-3", second.identity.gid)
    }

    @Test
    fun `delete removes persisted identity from store`() = runTest {
        val persisted = identity("gid-1")
        val replacement = identity("gid-2")
        val generator = QueuedIdentityGenerator(listOf(replacement))
        val store = RecordingIdentityStore(IdentityStoreState(listOf(persisted), activeIndex = 0))
        val repository = IdentityRepository(store, generator)

        val selection = repository.deleteCurrent()

        assertEquals(replacement, selection.identity)
        assertTrue(store.wasCleared)
        assertEquals(null, store.state)
        assertTrue(store.storedStates.isEmpty())
    }
}

private object UnexpectedIdentityGenerator : IdentityProvider {
    override suspend fun generate(): Identity = error("IdentityProvider should not be invoked")
}

private class StaticIdentityGenerator(private val identity: Identity) : IdentityProvider {
    override suspend fun generate(): Identity = identity
}

private class QueuedIdentityGenerator(
    private val queue: MutableList<Identity>,
) : IdentityProvider {
    constructor(items: List<Identity>) : this(items.toMutableList())

    override suspend fun generate(): Identity =
        queue.removeFirstOrNull() ?: error("No identities queued")
}

private fun identity(gid: String): Identity =
    Identity(
        gid = gid,
        keyPair = emptyCryptoKeyPair(),
    )

private fun emptyCryptoKeyPair(): CryptoKeyPair =
    js("{ privateKey: {}, publicKey: {} }").unsafeCast<CryptoKeyPair>()

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
