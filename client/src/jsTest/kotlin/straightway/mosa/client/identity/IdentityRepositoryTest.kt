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
