package straightway.mosa.client.ui

import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.unsafeCast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.browser.CryptoKeyPair
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryResult
import straightway.mosa.content.ReplyRequest
import straightway.mosa.identity.FictionalName
import kotlin.test.fail

@OptIn(ExperimentalCoroutinesApi::class)
class IdentitySessionStateMachineTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun `initial state uses loading placeholder`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val textProvider = FakeIdentitySessionTextProvider(loading = "Bitte warten")
        val machine = machine(repository = repository, textProvider = textProvider, scope = this)

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(FictionalName(listOf("Bitte warten"), ""), state.identityNameParts)
        assertFalse(state.isBusy)
        assertNull(state.errorText)
        assertFalse(state.canConnect)
        assertFalse(state.canDownload)
    }

    @Test
    fun `refresh updates landing view`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val machine = machine(repository = repository, scope = this)

        machine.refresh()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(0, state.identityIndex)
        assertEquals(1, state.identityTotal)
        assertTrue(state.canConnect)
        assertEquals(FictionalName(givenNames = listOf("Ada"), sureName = "Lovelace"), state.identityNameParts)
        assertEquals("avatar", state.identityAvatar)
        assertNull(state.errorText)
        assertTrue(state.canDownload)
    }

    @Test
    fun `refresh publishes failure state on error`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        repository.loadOrCreateAction = { throw RuntimeException("kaputt") }
        val textProvider = FakeIdentitySessionTextProvider()
        val logger = RecordingLogger()
        val machine = machine(repository = repository, textProvider = textProvider, logger = logger, scope = this)

        machine.refresh()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(textProvider.failedToPrepare("kaputt"), state.identityNameParts.givenNames.single())
        assertEquals("kaputt", state.errorText)
        assertFalse(state.canConnect)
        assertEquals(listOf("Identity refresh failed"), logger.errorEntries.map { it.message })
    }

    @Test
    fun `refresh substitutes unknown error for cancellations`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        repository.loadOrCreateAction = { throw CancellationException("stop") }
        val textProvider = FakeIdentitySessionTextProvider()
        val machine = machine(repository = repository, textProvider = textProvider, scope = this)

        machine.refresh()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(textProvider.unknownError, state.errorText)
        assertEquals(textProvider.failedToPrepare(textProvider.unknownError), state.identityNameParts.givenNames.single())
    }

    @Test
    fun `refresh ignores repeated invocation while busy`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val blocker = CompletableDeferred<Unit>()
        repository.loadOrCreateAction = {
            blocker.await()
            identitySelection()
        }
        val machine = machine(repository = repository, scope = this)

        machine.refresh()
        machine.refresh()
        assertEquals(1, repository.loadOrCreateCalls)

        blocker.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `navigate to next applies repository selection`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection(total = 2))
        repository.moveNextAction = { identitySelection(gid = "gid-456", index = 1, total = 2) }
        val visualsProvider = visualsFromIdentity()
        val machine = machine(repository = repository, visualsProvider = visualsProvider, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.navigateToNext()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(1, state.identityIndex)
        assertTrue(state.canNavigatePrevious)
        assertTrue(state.canNavigateNext)
        assertTrue(state.isShowingLastIdentity)
        assertEquals("avatar-gid-456", state.identityAvatar)
        assertEquals(FictionalName(listOf("gid-456"), "Hero"), state.identityNameParts)
    }

    @Test
    fun `navigation ignores null responses`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        repository.movePreviousAction = { null }
        val machine = machine(repository = repository, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.navigateToPrevious()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(0, state.identityIndex)
        assertFalse(state.canNavigatePrevious)
    }

    @Test
    fun `navigation surfaces loader errors`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        repository.moveNextAction = { throw RuntimeException("broken nav") }
        val logger = RecordingLogger()
        val machine = machine(repository = repository, logger = logger, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.navigateToNext()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals("broken nav", state.errorText)
        assertEquals(listOf("Identity navigation failed"), logger.errorEntries.map { it.message })
    }

    @Test
    fun `delete identity applies repository selection`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        repository.deleteAction = { identitySelection(gid = "gid-del", index = 0, total = 1) }
        val visualsProvider = visualsFromIdentity()
        val machine = machine(repository = repository, visualsProvider = visualsProvider, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.deleteIdentity()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(FictionalName(listOf("gid-del"), "Hero"), state.identityNameParts)
    }

    @Test
    fun `select applies repository selection`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection(total = 3))
        repository.selectAction = { index -> identitySelection(gid = "gid-$index", index = index, total = 3) }
        val machine = machine(repository = repository, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.select(2)
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(2, state.identityIndex)
        assertTrue(state.canNavigatePrevious)
    }

    @Test
    fun `refresh exposes missing pod hint when default blank`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val textProvider = FakeIdentitySessionTextProvider(missingPod = "kein pod")
        val machine = machine(repository = repository, textProvider = textProvider, scope = this, defaultPod = "")

        machine.refresh()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals("kein pod", state.errorText)
    }

    @Test
    fun `connect publishes connected view`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        val machine =
            machine(
                repository = repository,
                podAccessFactory = { podAccess },
                scope = this,
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Connected>(machine.state.value)
        assertEquals("Ada Lovelace", state.identityName)
        assertEquals("jwt-token", state.misJwt)
        assertEquals("header Anything\n- preview (0.9)", state.queryPreview)
        assertEquals(listOf("gid-123"), podAccess.approvedGids)
        assertEquals(QueryRequest("Anything") to "jwt-token", podAccess.lastQuery)
    }

    @Test
    fun `connect ignored without selection`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        val machine = machine(repository = repository, podAccessFactory = { podAccess }, scope = this)

        machine.connectToPod()
        advanceUntilIdle()

        assertTrue(podAccess.approvedGids.isEmpty())
        assertIs<IdentitySessionViewState.Landing>(machine.state.value)
    }

    @Test
    fun `connect requires pod configuration`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val textProvider = FakeIdentitySessionTextProvider(missingPod = "kein pod")
        val podAccess = RecordingIdentitySessionPodAccess()
        val machine =
            machine(
                repository = repository,
                scope = this,
                textProvider = textProvider,
                podAccessFactory = { podAccess },
                defaultPod = "",
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals("kein pod", state.errorText)
        assertTrue(podAccess.approvedGids.isEmpty())
    }

    @Test
    fun `connect obeys busy guard`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        val resume = CompletableDeferred<Unit>()
        podAccess.approveAction = { gid ->
            resume.await()
            PodCertificate(gid, 0, "sig")
        }
        val machine = machine(repository = repository, podAccessFactory = { podAccess }, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        machine.connectToPod()
        assertEquals(1, podAccess.approvedGids.size)

        resume.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `connect displays empty preview text`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        podAccess.queryAction = { _, _ -> QueryResult(entries = emptyList(), queryOnId = "qid") }
        val textProvider = FakeIdentitySessionTextProvider()
        val machine =
            machine(
                repository = repository,
                podAccessFactory = { podAccess },
                textProvider = textProvider,
                scope = this,
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Connected>(machine.state.value)
        assertEquals(textProvider.previewResultsEmpty("Anything"), state.queryPreview)
    }

    @Test
    fun `connect logs warning when preview query fails`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        podAccess.queryAction = { _, _ -> throw RuntimeException("preview fail") }
        val logger = RecordingLogger()
        val machine =
            machine(
                repository = repository,
                podAccessFactory = { podAccess },
                logger = logger,
                scope = this,
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Connected>(machine.state.value)
        assertEquals("preview fail", state.queryPreview)
        assertEquals(listOf("Prototype query failed"), logger.warnEntries.map { it.message })
    }

    @Test
    fun `connect preview uses class name when message blank`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        podAccess.queryAction = { _, _ -> throw BlankMessageError() }
        val machine = machine(repository = repository, podAccessFactory = { podAccess }, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Connected>(machine.state.value)
        assertEquals("BlankMessageError", state.queryPreview)
    }

    @Test
    fun `connect surfaces approve failures`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        podAccess.approveAction = { throw RuntimeException("approval broken") }
        val logger = RecordingLogger()
        val machine =
            machine(
                repository = repository,
                podAccessFactory = { podAccess },
                logger = logger,
                scope = this,
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals("approval broken", state.errorText)
        assertEquals(listOf("Pod connection failed"), logger.errorEntries.map { it.message })
    }

    @Test
    fun `connect surfaces unknown errors from jwt builder`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val podAccess = RecordingIdentitySessionPodAccess()
        val textProvider = FakeIdentitySessionTextProvider()
        val anonymousError = object : Throwable() {
            override val message: String = " "
        }
        val machine =
            machine(
                repository = repository,
                podAccessFactory = { podAccess },
                textProvider = textProvider,
                scope = this,
                misJwtBuilder = { _, _ -> throw anonymousError },
            )

        machine.refresh()
        advanceUntilIdle()
        machine.connectToPod()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(textProvider.unknownError, state.errorText)
    }

    @Test
    fun `download uses sanitized filename`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        val visuals =
            IdentityVisuals(
                FictionalName(
                    givenNames = listOf("ada", "ROB", "R2D2"),
                    sureName = "von Braun",
                    addendum = "alpha-42 Beta",
                ),
                avatar = "avatar",
            )
        val machine =
            machine(
                repository = repository,
                transfer = transfer,
                scope = this,
                visualsProvider = { visuals },
            )

        machine.refresh()
        advanceUntilIdle()
        machine.downloadIdentity()
        advanceUntilIdle()

        assertEquals("AdaROBR2D2Alpha42BetaVonBraun", transfer.downloaded.single().second)
    }

    @Test
    fun `download ignored without selection`() = runTest(dispatcher) {
        val transfer = RecordingIdentityTransferPort()
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val machine = machine(repository = repository, transfer = transfer, scope = this)

        machine.downloadIdentity()
        advanceUntilIdle()

        assertTrue(transfer.downloaded.isEmpty())
    }

    @Test
    fun `download logs failures`() = runTest(dispatcher) {
        val transfer = RecordingIdentityTransferPort()
        transfer.downloadAction = { _, _ -> throw RuntimeException("disk full") }
        val logger = RecordingLogger()
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val machine = machine(repository = repository, transfer = transfer, logger = logger, scope = this)

        machine.refresh()
        advanceUntilIdle()
        machine.downloadIdentity()
        advanceUntilIdle()

        assertEquals(listOf("Identity download failed"), logger.errorEntries.map { it.message })
    }

    @Test
    fun `upload imports uploaded identity`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        val importedIdentity = Identity("gid-999", emptyCryptoKeyPair())
        transfer.uploadAction = { importedIdentity }
        repository.importAction = { identity -> identitySelection(identity.gid, index = 1, total = 2) }
        val visualsProvider = visualsFromIdentity()
        val machine =
            machine(
                repository = repository,
                transfer = transfer,
                visualsProvider = visualsProvider,
                scope = this,
            )

        machine.uploadIdentity()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(1, state.identityIndex)
        assertEquals(FictionalName(listOf("gid-999"), "Hero"), state.identityNameParts)
    }

    @Test
    fun `upload returns early when nothing uploaded`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        transfer.uploadAction = { null }
        val machine = machine(repository = repository, transfer = transfer, scope = this)

        machine.uploadIdentity()
        advanceUntilIdle()

        assertEquals(0, repository.importInvocations)
        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertFalse(state.isBusy)
    }

    @Test
    fun `upload surfaces failures`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        transfer.uploadAction = { throw RuntimeException("upload fail") }
        val logger = RecordingLogger()
        val machine = machine(repository = repository, transfer = transfer, logger = logger, scope = this)

        machine.uploadIdentity()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals("upload fail", state.errorText)
        assertEquals(listOf("Identity upload failed"), logger.errorEntries.map { it.message })
    }

    @Test
    fun `upload obeys busy guard`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        val resume = CompletableDeferred<Unit>()
        var uploadCalls = 0
        transfer.uploadAction = suspend {
            uploadCalls++
            resume.await()
            Identity("gid-111", emptyCryptoKeyPair())
        }
        repository.importAction = { imported -> identitySelection(imported.gid, 0, 1) }
        val machine = machine(repository = repository, transfer = transfer, scope = this)

        machine.uploadIdentity()
        machine.uploadIdentity()
        assertEquals(1, uploadCalls)

        resume.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, repository.importInvocations)
    }

    @Test
    fun `upload falls back to unknown error when throwable anonymous`() = runTest(dispatcher) {
        val repository = RecordingIdentitySelectionRepository(identitySelection())
        val transfer = RecordingIdentityTransferPort()
        val unknown = object : Throwable() {
            override val message: String = " "
        }
        transfer.uploadAction = { throw unknown }
        val textProvider = FakeIdentitySessionTextProvider()
        val machine = machine(repository = repository, transfer = transfer, textProvider = textProvider, scope = this)

        machine.uploadIdentity()
        advanceUntilIdle()

        val state = assertIs<IdentitySessionViewState.Landing>(machine.state.value)
        assertEquals(textProvider.unknownError, state.errorText)
    }

    @Test
    fun `placeholderName trims and defaults`() {
        assertEquals(FictionalName(listOf("Name"), ""), placeholderName("  Name "))
        assertEquals(FictionalName(listOf("Identity"), ""), placeholderName("   "))
    }

    @Test
    fun `toIdentityDownloadFileName builds camel case tokens`() {
        val name =
            FictionalName(
                givenNames = listOf("ada", "ROB"),
                sureName = "van Allen",
                addendum = "von-neumann 42",
            )
        assertEquals("AdaROBVonNeumann42VanAllen", name.toIdentityDownloadFileName())
    }

    private fun machine(
        repository: RecordingIdentitySelectionRepository,
        scope: TestScope,
        transfer: RecordingIdentityTransferPort = RecordingIdentityTransferPort(),
        podAccessFactory: (String) -> PodAccess = { RecordingIdentitySessionPodAccess() },
        textProvider: FakeIdentitySessionTextProvider = FakeIdentitySessionTextProvider(),
        visualsProvider: (Identity) -> IdentityVisuals = { IdentityVisuals(FictionalName(listOf("Ada"), "Lovelace"), "avatar") },
        logger: RecordingLogger = RecordingLogger(),
        misJwtBuilder: suspend (Identity, PodCertificate) -> String = { _, _ -> "jwt-token" },
        defaultPod: String = "http://pod",
    ): IdentitySessionStateMachine =
        IdentitySessionStateMachine(
            repository = repository,
            transfer = transfer,
            misJwtBuilder = misJwtBuilder,
            podAccessFactory = podAccessFactory,
            textProvider = textProvider,
            visualsProvider = visualsProvider,
            defaultPod = defaultPod,
            scope = scope,
            logger = logger,
        )

    private fun visualsFromIdentity(): (Identity) -> IdentityVisuals =
        { identity ->
            IdentityVisuals(
                name = FictionalName(listOf(identity.gid), "Hero"),
                avatar = "avatar-${identity.gid}",
            )
        }
}

private class RecordingIdentitySelectionRepository(
    private val defaultSelection: IdentitySelection,
) : IdentitySelectionRepository {
    var loadOrCreateCalls = 0
    var movePreviousCalls = 0
    var moveNextCalls = 0
    var deleteCalls = 0
    var importInvocations = 0
    var selectInvocations = 0

    var loadOrCreateAction: suspend () -> IdentitySelection = { defaultSelection }
    var movePreviousAction: suspend () -> IdentitySelection? = { defaultSelection }
    var moveNextAction: suspend () -> IdentitySelection = { defaultSelection }
    var deleteAction: suspend () -> IdentitySelection = { defaultSelection }
    var selectAction: suspend (Int) -> IdentitySelection? = { defaultSelection }
    var importAction: suspend (Identity) -> IdentitySelection = { defaultSelection }

    override suspend fun loadOrCreate(): IdentitySelection {
        loadOrCreateCalls++
        return loadOrCreateAction()
    }

    override suspend fun movePrevious(): IdentitySelection? {
        movePreviousCalls++
        return movePreviousAction()
    }

    override suspend fun moveNext(): IdentitySelection {
        moveNextCalls++
        return moveNextAction()
    }

    override suspend fun deleteCurrent(): IdentitySelection {
        deleteCalls++
        return deleteAction()
    }

    override suspend fun select(index: Int): IdentitySelection? {
        selectInvocations++
        return selectAction(index)
    }

    override suspend fun import(identity: Identity): IdentitySelection {
        importInvocations++
        return importAction(identity)
    }
}

private class RecordingIdentitySessionPodAccess : PodAccess {
    val approvedGids = mutableListOf<String>()
    var lastQuery: Pair<QueryRequest, String>? = null
    var approveAction: suspend (String) -> PodCertificate = { gid -> PodCertificate(gid = gid, exp = 0, sig = "sig") }
    var queryAction: suspend (QueryRequest, String) -> QueryResult = { _, _ -> defaultQueryResult() }

    override suspend fun approveGid(gid: String): PodCertificate {
        approvedGids += gid
        return approveAction(gid)
    }

    override suspend fun publish(request: PublishRequest, jwt: String) = fail("Not implemented")

    override suspend fun reply(request: ReplyRequest, jwt: String) = fail("Not implemented")

    override suspend fun query(request: QueryRequest, jwt: String): QueryResult {
        lastQuery = request to jwt
        return queryAction(request, jwt)
    }

    override suspend fun queryOn(request: QueryOnRequest, jwt: String): QueryResult = fail("Not implemented")
}

private class RecordingIdentityTransferPort : IdentityTransferPort {
    val downloaded = mutableListOf<Pair<Identity, String>>()
    var downloadAction: suspend (Identity, String) -> Unit = { identity, fileBaseName ->
        downloaded += identity to fileBaseName
    }
    var uploadAction: suspend () -> Identity? = { null }

    override suspend fun download(identity: Identity, fileBaseName: String) {
        downloadAction(identity, fileBaseName)
    }

    override suspend fun upload(): Identity? = uploadAction()
}

private class FakeIdentitySessionTextProvider(
    override val loading: String = "loading",
    override val unknownError: String = "unknown",
    override val missingPod: String = "missing",
    private val failedTemplate: (String) -> String = { message -> "failed $message" },
    private val emptyTemplate: (String) -> String = { topic -> "empty $topic" },
    private val headerTemplate: (String) -> String = { topic -> "header $topic" },
) : IdentitySessionTextProvider {
    override fun failedToPrepare(message: String): String = failedTemplate(message)
    override fun previewResultsEmpty(topic: String): String = emptyTemplate(topic)
    override fun previewResultsHeader(topic: String): String = headerTemplate(topic)
}

private class RecordingLogger : Logger {
    data class Entry(val message: String, val throwable: Throwable?)

    val errorEntries = mutableListOf<Entry>()
    val warnEntries = mutableListOf<Entry>()

    override fun error(message: String, throwable: Throwable?) {
        errorEntries += Entry(message, throwable)
    }

    override fun warn(message: String, throwable: Throwable?) {
        warnEntries += Entry(message, throwable)
    }
}

private class BlankMessageError : Throwable("   ")

private fun defaultQueryResult(): QueryResult =
    QueryResult(
        entries = listOf(
            QueryResult.Entry(
                id = "1",
                content = "preview",
                similarity = 0.9f,
                authorGID = "gid-123",
                storedAt = 0,
            ),
        ),
        queryOnId = "qid",
    )

private fun identitySelection(
    gid: String = "gid-123",
    index: Int = 0,
    total: Int = 1,
): IdentitySelection =
    IdentitySelection(
        identity = Identity(gid = gid, keyPair = emptyCryptoKeyPair()),
        index = index,
        total = total,
    )

private fun emptyCryptoKeyPair(): CryptoKeyPair =
    js("{ privateKey: {}, publicKey: {} }").unsafeCast<CryptoKeyPair>()
