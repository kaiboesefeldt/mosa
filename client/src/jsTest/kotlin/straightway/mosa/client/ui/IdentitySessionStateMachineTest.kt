@file:OptIn(ExperimentalCoroutinesApi::class)

package straightway.mosa.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.js.js
import straightway.mosa.client.browser.CryptoKey
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.browser.SubtleCrypto
import straightway.mosa.client.content.PostsExportPort
import straightway.mosa.client.content.PrivacySnapshotPort
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.InvalidIdentityTransferException
import straightway.mosa.client.identity.IdentityProtector
import straightway.mosa.client.identity.PasswordPrompter
import straightway.mosa.client.identity.CryptoExport
import straightway.mosa.client.logging.Logger
import straightway.mosa.identity.FictionalName

class IdentitySessionStateMachineTest {
    @Test
    fun `initial state stays loading until refresh completes`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore(IdentitySessionSnapshot(identity.gid, false))

        val machine = createMachine(repository, store)
        advanceUntilIdle()

        val initialState = machine.state.value
        assertTrue(initialState is IdentitySessionViewState.Loading)

        machine.refresh()
        advanceUntilIdle()

        val restoringState = machine.state.value
        assertTrue(restoringState is IdentitySessionViewState.Connected)
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
    fun `preserves persisted connection when browsing other identities`() = runMachineTest {
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
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(listOf(identityB.gid), state.profile.identityNameParts.givenNames)
        assertEquals(false, state.profile.canDeletePosts)
        assertEquals(identityA.gid, state.profile.connectedIdentityGid)
        assertEquals(identityA.gid, store.snapshot?.connectedIdentityGid)
    }

    @Test
    fun `restores connected state when returning to previously connected identity`() = runMachineTest {
        val identityA = identity("gid-a")
        val identityB = identity("gid-b")
        val repository = FakeIdentitySelectionRepository(identityA, identityB)
        val store = RecordingIdentitySessionStore()
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()
        machine.navigateToNext()
        advanceUntilIdle()

        val switchedState = machine.state.value
        assertTrue(switchedState is IdentitySessionViewState.Connected)
        assertEquals(listOf(identityB.gid), switchedState.profile.identityNameParts.givenNames)

        machine.navigateToPrevious()
        advanceUntilIdle()

        val restoredState = machine.state.value
        assertTrue(restoredState is IdentitySessionViewState.Connected)
        assertEquals(identityA.gid, restoredState.profile.connectedIdentityGid)
        assertEquals(true, restoredState.profile.canDeletePosts)
        assertEquals(identityA.gid, store.snapshot?.connectedIdentityGid)
    }

    @Test
    fun `tracks displayed and connected identity relation across navigation matrix`() = runMachineTest {
        val identityA = identity("gid-a")
        val identityB = identity("gid-b")
        val repository = FakeIdentitySelectionRepository(identityA, identityB)
        val store = RecordingIdentitySessionStore()
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()
        assertProfileBinding(machine.state.value, displayedGid = identityA.gid, connectedGid = null, canDeletePosts = false)

        machine.connectToPod()
        advanceUntilIdle()
        assertProfileBinding(machine.state.value, displayedGid = identityA.gid, connectedGid = identityA.gid, canDeletePosts = true)

        machine.navigateToNext()
        advanceUntilIdle()
        assertProfileBinding(machine.state.value, displayedGid = identityB.gid, connectedGid = identityA.gid, canDeletePosts = false)

        machine.connectToPod()
        advanceUntilIdle()
        assertProfileBinding(machine.state.value, displayedGid = identityB.gid, connectedGid = identityB.gid, canDeletePosts = true)

        machine.navigateToPrevious()
        advanceUntilIdle()
        assertProfileBinding(machine.state.value, displayedGid = identityA.gid, connectedGid = identityB.gid, canDeletePosts = false)
    }

    @Test
    fun `persists identities when connecting`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val machine = createMachine(repository, store)

        machine.refresh()
        advanceUntilIdle()

        machine.connectToPod()
        advanceUntilIdle()

        assertEquals(1, repository.persistCalls)
    }

    @Test
    fun `deletes connected identity on pod before removing it locally`() = runMachineTest {
        val firstIdentity = identity("gid-a")
        val secondIdentity = identity("gid-b")
        val repository = FakeIdentitySelectionRepository(firstIdentity, secondIdentity)
        val store = RecordingIdentitySessionStore()
        var deleteCalls = 0
        val machine =
            createMachine(
                repository = repository,
                store = store,
                deleteIdentityOnPod = {
                    deleteCalls += 1
                    true
                },
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.deleteIdentity()
        advanceUntilIdle()

        assertEquals(1, deleteCalls)
        assertEquals(1, repository.deleteCalls)
        assertEquals(null, store.snapshot?.connectedIdentityGid)
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Profile)
        assertEquals(secondIdentity.gid, state.identityGid)
        assertFalse(state.canDelete)
    }

    @Test
    fun `keeps identity when pod deletion fails`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        var deleteCalls = 0
        val machine =
            createMachine(
                repository = repository,
                store = store,
                deleteIdentityOnPod = {
                    deleteCalls += 1
                    false
                },
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.deleteIdentity()
        advanceUntilIdle()

        assertEquals(1, deleteCalls)
        assertEquals(0, repository.deleteCalls)
        assertEquals(identity.gid, store.snapshot?.connectedIdentityGid)
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(TestIdentitySessionTextProvider.deleteError, state.profile.errorText)
        assertTrue(state.profile.canDelete)
    }

    @Test
    fun `imports identity from clipboard`() = runMachineTest {
        val initialIdentity = identity("gid-a")
        val pastedIdentity = identity("gid-b")
        val repository = FakeIdentitySelectionRepository(initialIdentity)
        val store = RecordingIdentitySessionStore()
        val transfer =
            object : IdentityTransferPort {
                override suspend fun download(protectedExport: String, fileBaseName: String) = Unit

                override suspend fun copyToClipboard(protectedExport: String) = Unit

                override suspend fun copyTextToClipboard(content: String) = Unit

                override suspend fun upload(): String? = null

                override suspend fun pasteFromClipboard(): String = "serialized"
            }
        val machine = createMachine(
            repository = repository,
            store = store,
            transfer = transfer,
            protector = StubProtector(pastedIdentity),
            passwordPrompter = StaticPasswordPrompter("pw"),
        )

        machine.refresh()
        advanceUntilIdle()

        machine.pasteIdentityFromClipboard()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Profile)
        assertEquals(pastedIdentity.gid, state.identityNameParts.givenNames.first())
    }

    @Test
    fun `shows error when clipboard identity is invalid`() = runMachineTest {
        val initialIdentity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(initialIdentity)
        val store = RecordingIdentitySessionStore()
        val transfer =
            object : IdentityTransferPort {
                override suspend fun download(protectedExport: String, fileBaseName: String) = Unit

                override suspend fun copyToClipboard(protectedExport: String) = Unit

                override suspend fun copyTextToClipboard(content: String) = Unit

                override suspend fun upload(): String? = null

                override suspend fun pasteFromClipboard(): String =
                    throw InvalidIdentityTransferException("corrupted")
            }
        val machine = createMachine(
            repository = repository,
            store = store,
            transfer = transfer,
            protector = StubProtector(initialIdentity),
            passwordPrompter = StaticPasswordPrompter("pw"),
        )

        machine.refresh()
        advanceUntilIdle()

        machine.pasteIdentityFromClipboard()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Profile)
        assertEquals(TestIdentitySessionTextProvider.invalidIdentity, state.errorText)
    }

    @Test
    fun `exports posts csv when identity is connected`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val export = RecordingPostsExportPort()
        val machine = createMachine(repository, store, postsExport = export)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.exportPostsCsv()
        advanceUntilIdle()

        assertEquals(listOf("Gida"), export.fileBaseNames)
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(null, state.profile.statusText)
    }

    @Test
    fun `shows export progress while csv export is running`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val blocker = CompletableDeferred<Unit>()
        val export =
            object : PostsExportPort {
                override suspend fun export(fileBaseName: String) {
                    blocker.await()
                }
            }
        val machine = createMachine(repository, store, postsExport = export)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.exportPostsCsv()

        val duringExport = machine.state.value
        assertTrue(duringExport is IdentitySessionViewState.Connected)
        assertEquals(TestIdentitySessionTextProvider.exportInProgress, duringExport.profile.statusText)

        blocker.complete(Unit)
        advanceUntilIdle()

        val afterExport = machine.state.value
        assertTrue(afterExport is IdentitySessionViewState.Connected)
        assertEquals(null, afterExport.profile.statusText)
    }

    @Test
    fun `shows export error when csv export fails`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val export =
            object : PostsExportPort {
                override suspend fun export(fileBaseName: String) {
                    throw IllegalStateException("boom")
                }
            }
        val machine = createMachine(repository, store, postsExport = export)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.exportPostsCsv()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(TestIdentitySessionTextProvider.exportError, state.profile.errorText)
        assertEquals(null, state.profile.statusText)
    }

    @Test
    fun `freezes posts after confirmation when identity is connected`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val snapshots = RecordingPrivacySnapshotPort()
        val machine = createMachine(repository, store, privacySnapshots = snapshots)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.freezePosts()
        advanceUntilIdle()

        assertEquals(listOf("mosa-snapshot"), snapshots.freezeFileBaseNames)
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(TestIdentitySessionTextProvider.freezeSuccess, state.profile.statusText)
        assertEquals(1, state.profile.snapshotRefreshVersion)
    }

    @Test
    fun `shows freeze error when snapshot freeze fails`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val snapshots =
            object : PrivacySnapshotPort {
                override suspend fun freeze(fileBaseName: String) {
                    throw IllegalStateException("boom")
                }

                override suspend fun restore() = null
            }
        val machine = createMachine(repository, store, privacySnapshots = snapshots)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.freezePosts()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(TestIdentitySessionTextProvider.freezeError, state.profile.errorText)
        assertEquals(null, state.profile.statusText)
    }

    @Test
    fun `restores posts snapshot when identity is connected`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val snapshots = RecordingPrivacySnapshotPort(restoreResult = straightway.mosa.content.FreezeSnapshotRestoreResult(listOf("a", "b"), 2))
        val machine = createMachine(repository, store, privacySnapshots = snapshots)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.restorePosts()
        advanceUntilIdle()

        assertEquals(1, snapshots.restoreCalls)
        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals("restored 2", state.profile.statusText)
        assertEquals(1, state.profile.snapshotRefreshVersion)
    }

    @Test
    fun `keeps state unchanged when restore is cancelled`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val snapshots = RecordingPrivacySnapshotPort(restoreResult = null)
        val machine = createMachine(repository, store, privacySnapshots = snapshots)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        machine.restorePosts()
        advanceUntilIdle()

        val state = machine.state.value
        assertTrue(state is IdentitySessionViewState.Connected)
        assertEquals(null, state.profile.statusText)
        assertEquals(null, state.profile.errorText)
        assertEquals(0, state.profile.snapshotRefreshVersion)
    }

    @Test
    fun `copies gid to clipboard for current identity`() = runMachineTest {
        val identity = identity("gid-a")
        val repository = FakeIdentitySelectionRepository(identity)
        val store = RecordingIdentitySessionStore()
        val transfer = RecordingIdentityTransferPort()
        val machine = createMachine(repository, store, transfer = transfer)

        machine.refresh()
        advanceUntilIdle()

        machine.copyGidToClipboard()
        advanceUntilIdle()

        assertEquals(listOf("gid-a"), transfer.copiedTexts)
    }
}

private fun TestScope.createMachine(
    repository: IdentitySelectionRepository,
    store: IdentitySessionStore,
    transfer: IdentityTransferPort = NoopIdentityTransferPort,
    postsExport: PostsExportPort = NoopPostsExportPort,
    privacySnapshots: PrivacySnapshotPort = NoopPrivacySnapshotPort,
    deleteIdentityOnPod: suspend () -> Boolean = { true },
    protector: IdentityProtector = StubProtector(identity("imported")),
    passwordPrompter: PasswordPrompter = StaticPasswordPrompter("pw"),
): IdentitySessionStateMachine =
    IdentitySessionStateMachine(
        repository = repository,
        transfer = transfer,
        postsExport = postsExport,
        privacySnapshots = privacySnapshots,
        deleteIdentityOnPod = deleteIdentityOnPod,
        protector = protector,
        passwordPrompter = passwordPrompter,
        textProvider = TestIdentitySessionTextProvider,
        visualsProvider = { identity ->
            IdentityVisuals(FictionalName(listOf(identity.gid)), identity.gid)
        },
        scope = this,
        logger = TestLogger,
        sessionStore = store,
    )

private fun assertProfileBinding(
    state: IdentitySessionViewState,
    displayedGid: String?,
    connectedGid: String?,
    canDeletePosts: Boolean,
) {
    val profile =
        when (state) {
            is IdentitySessionViewState.Connected -> state.profile
            is IdentitySessionViewState.Profile -> state
            IdentitySessionViewState.Loading -> error("loading state has no profile binding")
        }
    assertEquals(displayedGid, profile.identityGid)
    assertEquals(connectedGid, profile.connectedIdentityGid)
    assertEquals(canDeletePosts, profile.canDeletePosts)
}

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
    var persistCalls = 0
    var deleteCalls = 0

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
        deleteCalls += 1
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

    override suspend fun protectCurrent(password: String): IdentitySelection = currentSelection()

    override suspend fun persist() {
        persistCalls += 1
    }

    private fun currentSelection(): IdentitySelection =
        IdentitySelection(
            identity = identities[index],
            index = index,
            total = identities.size,
            isPersisted = true,
        )
}

private object TestIdentitySessionTextProvider : IdentitySessionTextProvider {
    override val loading: String = "loading"
    override val unknownError: String = "error"
    override val invalidIdentity: String = "invalid"
    override val exportInProgress: String = "exporting"
    override val exportError: String = "export error"
    override val deleteError: String = "delete error"
    override val freezeInProgress: String = "freezing"
    override val freezeSuccess: String = "freeze success"
    override val freezeError: String = "freeze error"
    override val restoreInProgress: String = "restoring"
    override val restoreError: String = "restore error"

    override fun failedToPrepare(message: String): String = message

    override fun restoreSuccess(restoredCount: Int): String = "restored $restoredCount"
}

private object TestLogger : Logger {
    override fun error(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit
}

private object NoopIdentityTransferPort : IdentityTransferPort {
    override suspend fun download(protectedExport: String, fileBaseName: String) = Unit

    override suspend fun copyToClipboard(protectedExport: String) = Unit

    override suspend fun copyTextToClipboard(content: String) = Unit

    override suspend fun upload(): String? = null

    override suspend fun pasteFromClipboard(): String =
        throw UnsupportedOperationException("Not used in test")
}

private class RecordingIdentityTransferPort : IdentityTransferPort {
    val copiedTexts = mutableListOf<String>()

    override suspend fun download(protectedExport: String, fileBaseName: String) = Unit

    override suspend fun copyToClipboard(protectedExport: String) = Unit

    override suspend fun copyTextToClipboard(content: String) {
        copiedTexts += content
    }

    override suspend fun upload(): String? = null

    override suspend fun pasteFromClipboard(): String =
        throw UnsupportedOperationException("Not used in test")
}

private object NoopPostsExportPort : PostsExportPort {
    override suspend fun export(fileBaseName: String) = Unit
}

private object NoopPrivacySnapshotPort : PrivacySnapshotPort {
    override suspend fun freeze(fileBaseName: String) = Unit

    override suspend fun restore() = null
}

private class RecordingPostsExportPort : PostsExportPort {
    val fileBaseNames = mutableListOf<String>()

    override suspend fun export(fileBaseName: String) {
        fileBaseNames += fileBaseName
    }
}

private class RecordingPrivacySnapshotPort(
    private val restoreResult: straightway.mosa.content.FreezeSnapshotRestoreResult? = null,
) : PrivacySnapshotPort {
    val freezeFileBaseNames = mutableListOf<String>()
    var restoreCalls = 0

    override suspend fun freeze(fileBaseName: String) {
        freezeFileBaseNames += fileBaseName
    }

    override suspend fun restore(): straightway.mosa.content.FreezeSnapshotRestoreResult? {
        restoreCalls += 1
        return restoreResult
    }
}

private class StaticPasswordPrompter(
    private val password: String?,
) : PasswordPrompter {
    override suspend fun promptNewPassword(): String? = password

    override suspend fun promptExistingPassword(): String? = password
}

private class StubProtector(
    private val imported: Identity,
) : IdentityProtector(SubtleCrypto(js("{}")), CryptoExport(SubtleCrypto(js("{}")))) {
    override suspend fun protect(identity: Identity, password: String): Identity =
        identity.copy(protectedExport = "protected")

    override suspend fun import(serialized: String, password: String): Identity = imported
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
