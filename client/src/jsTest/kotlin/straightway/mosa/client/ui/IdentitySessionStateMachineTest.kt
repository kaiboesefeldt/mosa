@file:OptIn(ExperimentalCoroutinesApi::class)

package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.logging.Logger
import straightway.mosa.identity.FictionalName

class IdentitySessionStateMachineTest {
    @Test
    fun `initial state shows connection when session persists gid`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore(IdentitySessionSnapshot(identity.gid, false))

        val machine = createMachine(repository, store)
        advanceUntilIdle()
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
    }

    @Test
    fun `restores connection state from persisted session`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore(IdentitySessionSnapshot(identity.gid, false))
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(identity.gid, store.snapshot?.connectedIdentityGid)
    }

    @Test
    fun `keeps profile visibility when restoring session`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore(IdentitySessionSnapshot(identity.gid, true))
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(true, store.snapshot?.isProfileVisible)
    }

    @Test
    fun `clears persisted connection when switching identity`() = runMachineTest {
        val identityA = identity("gid-a")
        val identityB = identity("gid-b")
        val repository = FakeIdentitySelectionRepository(identityA, identityB)
        val store = RecordingIdentitySessionStore()
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        assertEquals(identityA.gid, store.snapshot?.connectedIdentityGid)

        machine.navigateToNext()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Profile)
        assertNull(store.snapshot?.connectedIdentityGid)
    }
}

private fun TestScope.createMachine(
    repository: IdentitySelectionRepository,
    store: IdentitySessionStore,
): IdentitySessionStateMachine =
    IdentitySessionStateMachine(
        repository = repository,
        transfer = NoopIdentityTransferPort,
        textProvider = TestIdentitySessionTextProvider,
        visualsProvider = { identity ->
            IdentityVisuals(FictionalName(listOf(identity.gid)), identity.gid)
        },
        scope = this,
        logger = TestLogger,
        sessionStore = store,
    )

private fun identity(id: String): Identity =
    Identity(
        gid = id,
        keyPair = object : CryptoKeyPair {
            override val privateKey: CryptoKey = object : CryptoKey {}

            override val publicKey: CryptoKey = object : CryptoKey {}
        },
    )

@OptIn(ExperimentalCoroutinesApi::class)
private fun runMachineTest(block: suspend TestScope.() -> Unit) = runTest { block() }

private class FakeIdentitySelectionRepository(
    first: Identity,
    second: Identity? = null,
) : IdentitySelectionRepository {
    private val identities = mutableListOf(first).apply { second?.let { add(it) } }
    private var index = 0

    override suspend fun loadOrCreate(): IdentitySelection =
        currentSelection()

    override suspend fun movePrevious(): IdentitySelection? {
        if (index <= 0) return null
        index -= 1
        return currentSelection()
    }

    override suspend fun moveNext(): IdentitySelection {
        if (index < identities.lastIndex) index += 1
        return currentSelection()
    }

    override suspend fun deleteCurrent(): IdentitySelection {
        identities.removeAt(index.coerceIn(identities.indices))
        index = index.coerceAtMost(identities.lastIndex)
        return currentSelection()
    }

    override suspend fun select(index: Int): IdentitySelection? {
        if (index !in identities.indices) return null
        this.index = index
        return currentSelection()
    }

    override suspend fun import(identity: Identity): IdentitySelection {
        val existingIndex = identities.indexOfFirst { it.gid == identity.gid }
        index =
            if (existingIndex >= 0) existingIndex else identities.size.also { identities.add(identity) }
        return currentSelection()
    }

    private fun currentSelection(): IdentitySelection =
        IdentitySelection(
            identity = identities[index],
            index = index,
            total = identities.size,
        )
}

private object TestIdentitySessionTextProvider : IdentitySessionTextProvider {
    override val loading: String = "loading"
    override val unknownError: String = "error"

    override fun failedToPrepare(message: String): String = message
}

private object TestLogger : Logger {
    override fun error(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

private object NoopIdentityTransferPort : IdentityTransferPort {
    override suspend fun download(identity: Identity, fileBaseName: String) = Unit

    override suspend fun upload(): Identity? = null
}

private class RecordingIdentitySessionStore(
    initial: IdentitySessionSnapshot? = null,
) : IdentitySessionStore {
    var snapshot: IdentitySessionSnapshot? = initial
        private set

    override suspend fun load(): IdentitySessionSnapshot? = snapshot

    override suspend fun save(snapshot: IdentitySessionSnapshot) {
        this.snapshot = snapshot
    }
}
