package straightway.mosa.client.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlin.js.unsafeCast
import straightway.mosa.client.browser.CryptoKeyPair

class IdentityRepositoryTest {
    @Test
    fun `select moves to requested index when available`() = runTest {
        val first = identity("gid-1")
        val second = identity("gid-2")
        val third = identity("gid-3")
        val initialState = IdentityStoreState(listOf(first, second, third), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)

        val selection = repository.select(2)

        assertEquals(2, selection?.index)
        assertEquals(third, selection?.identity)
        assertEquals(1, store.storedStates.size)
        assertEquals(2, store.storedStates.single().activeIndex)
    }

    @Test
    fun `select ignores indexes outside stored range`() = runTest {
        val first = identity("gid-1")
        val second = identity("gid-2")
        val initialState = IdentityStoreState(listOf(first, second), activeIndex = 1)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)

        val selection = repository.select(5)

        assertEquals(1, selection?.index)
        assertEquals(second, selection?.identity)
        assertTrue(store.storedStates.isEmpty())
    }

    @Test
    fun `import stores identity when none persisted`() = runTest {
        val store = RecordingIdentityStore(null)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)
        val uploaded = identity("gid-42")

        val selection = repository.import(uploaded)

        val stored = store.state
        assertEquals(uploaded, selection.identity)
        assertEquals(0, selection.index)
        assertEquals(1, selection.total)
        assertEquals(uploaded, stored?.current())
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

        assertEquals(second, selection.identity)
        assertEquals(1, selection.index)
        assertEquals(2, selection.total)
        assertEquals(1, store.state?.activeIndex)
        assertTrue(store.storedStates.single().identities === store.state?.identities)
    }

    @Test
    fun `import appends identity when gid unknown`() = runTest {
        val first = identity("gid-1")
        val initialState = IdentityStoreState(listOf(first), activeIndex = 0)
        val store = RecordingIdentityStore(initialState)
        val repository = IdentityRepository(store, generator = UnexpectedIdentityGenerator)
        val uploaded = identity("gid-99")

        val selection = repository.import(uploaded)

        val stored = store.state
        assertEquals(uploaded, selection.identity)
        assertEquals(1, selection.index)
        assertEquals(2, selection.total)
        assertEquals(2, stored?.identities?.size)
        assertEquals(uploaded, stored?.current())
    }
}

private object UnexpectedIdentityGenerator : IdentityProvider {
    override suspend fun generate(): Identity = error("IdentityProvider should not be invoked")
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

    override suspend fun loadState(): IdentityStoreState? = state

    override suspend fun storeState(state: IdentityStoreState) {
        storedStates += state
        this.state = state
    }
}
