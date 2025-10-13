package straightway.mosa.client.identity

import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.test.ensureStacktraceMapperInstalled
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IdentityRepositoryTest {

    @BeforeTest
    fun installStacktraceMapper() {
        ensureStacktraceMapperInstalled()
    }

    @Test
    fun `loadOrCreate returns persisted identity without regenerating`() = runTest {
        val persisted = identity("persisted")
        val store = RecordingIdentityStore(initialIdentity = persisted)
        val generator = RecordingIdentityGenerator(identity("new"))
        val repository = IdentityRepository(store, generator)

        val result = repository.loadOrCreate()

        assertSame(persisted, result)
        assertEquals(1, store.loadCallCount)
        assertTrue(store.storedIdentities.isEmpty())
        assertTrue(generator.generatedIdentities.isEmpty())
    }

    @Test
    fun `loadOrCreate generates and stores when no identity exists`() = runTest {
        val generated = identity("generated")
        val store = RecordingIdentityStore(initialIdentity = null)
        val generator = RecordingIdentityGenerator(generated)
        val repository = IdentityRepository(store, generator)

        val result = repository.loadOrCreate()

        assertSame(generated, result)
        assertEquals(1, store.loadCallCount)
        assertEquals(listOf(generated), store.storedIdentities)
        assertEquals(listOf(generated), generator.generatedIdentities)
        assertSame(generated, store.currentIdentity)
    }

    @Test
    fun `regenerate always replaces stored identity`() = runTest {
        val initial = identity("initial")
        val regeneratedFirst = identity("regen-1")
        val regeneratedSecond = identity("regen-2")
        val store = RecordingIdentityStore(initialIdentity = initial)
        val generator = RecordingIdentityGenerator(regeneratedFirst, regeneratedSecond)
        val repository = IdentityRepository(store, generator)

        val first = repository.regenerate()
        val second = repository.regenerate()

        assertSame(regeneratedFirst, first)
        assertSame(regeneratedSecond, second)
        assertEquals(listOf(regeneratedFirst, regeneratedSecond), store.storedIdentities)
        assertEquals(listOf(regeneratedFirst, regeneratedSecond), generator.generatedIdentities)
        assertEquals(0, store.loadCallCount)
        assertSame(regeneratedSecond, store.currentIdentity)
    }

    private class RecordingIdentityStore(
        initialIdentity: Identity?
    ) : IdentityStore {
        private val loadResponses = ArrayDeque<Identity?>()
        var currentIdentity: Identity? = initialIdentity
            private set
        val storedIdentities = mutableListOf<Identity>()
        var loadCallCount = 0
            private set

        override suspend fun load(): Identity? {
            loadCallCount++
            return if (loadResponses.isNotEmpty()) {
                loadResponses.removeFirst()
            } else {
                currentIdentity
            }
        }

        override suspend fun store(identity: Identity) {
            storedIdentities += identity
            currentIdentity = identity
        }
    }

    private class RecordingIdentityGenerator(
        vararg identities: Identity
    ) : IdentityProvider {
        private val queue = ArrayDeque(identities.asList())
        val generatedIdentities = mutableListOf<Identity>()

        override suspend fun generate(): Identity {
            val next = queue.removeFirstOrNull() ?: error("No identity prepared for test")
            generatedIdentities += next
            return next
        }
    }

    private fun identity(label: String): Identity {
        val keyPair = js("({})")
        keyPair.publicKey = js("({ label: label + '-pub' })")
        keyPair.privateKey = js("({ label: label + '-priv' })")
        return Identity(
            gid = "gid-$label",
            keyPair = keyPair.unsafeCast<CryptoKeyPair>()
        )
    }
}
