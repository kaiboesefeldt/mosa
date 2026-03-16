package straightway.mosa.client.ui

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import straightway.mosa.client.content.PostsExportPort
import straightway.mosa.client.content.PrivacySnapshotPort
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.InvalidIdentityTransferException
import straightway.mosa.client.identity.IdentityProtector
import straightway.mosa.client.identity.PasswordPrompter
import straightway.mosa.client.logging.Logger

class IdentitySessionStateMachine(
    private val repository: IdentitySelectionRepository,
    private val transfer: IdentityTransferPort,
    private val postsExport: PostsExportPort,
    private val privacySnapshots: PrivacySnapshotPort,
    private val protector: IdentityProtector,
    private val passwordPrompter: PasswordPrompter,
    private val textProvider: IdentitySessionTextProvider,
    private val visualsProvider: (Identity) -> IdentityVisuals,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val sessionStore: IdentitySessionStore,
) {
    private data class IdentityBinding(
        val displayedIdentityGid: String?,
        val connectedIdentityGid: String?,
    ) {
        val isDisplayedIdentityConnected: Boolean = displayedIdentityGid != null && displayedIdentityGid == connectedIdentityGid
    }

    private var currentSelection: IdentitySelection? = null
    private var identityNameParts = placeholderName(textProvider.loading)
    private var identityAvatar: String? = null
    private var isBusy = false
    private var errorText: String? = null
    private var statusText: String? = null
    private var connectedIdentityGid: String? = null
    private var isRestoring = true
    private var snapshotRefreshVersion = 0

    private val _state = MutableStateFlow<IdentitySessionViewState>(IdentitySessionViewState.Loading)
    val state: StateFlow<IdentitySessionViewState> = _state

    init {
        scope.launch {
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            connectedIdentityGid = snapshot?.connectedIdentityGid
            val wasConnected = connectedIdentityGid != null
            if (wasConnected) {
                publishState()
            }
        }
    }

    fun refresh() {
        runExclusive(
            failureMessage = "Identity refresh failed",
            beforeRun = { identityAvatar = null },
            onFailure = ::handlePreparationFailure,
        ) {
            val selection = repository.loadOrCreate()
            applySelection(selection, profileVisibleOverride = null)
        }
    }

    fun navigateToPrevious() = navigate("Identity navigation failed") { movePrevious() }

    fun navigateToNext() = navigate("Identity navigation failed") { moveNext() }

    fun deleteIdentity() = navigate("Identity removal failed") { deleteCurrent() }

    fun select(index: Int) = navigate("Identity navigation failed") { select(index) }

    fun connectToPod() {
        runExclusive(
            failureMessage = "Identity connection failed",
            onFailure = ::handleFailure,
        ) {
            val selection = ensureProtectedSelection() ?: return@runExclusive
            currentSelection = selection
            connectedIdentityGid = selection.identity.gid
            errorText = null
            runCatching { repository.persist() }
                .onFailure { logger.error("Identity persistence failed", it) }
                .onSuccess { currentSelection = currentSelection?.copy(isPersisted = true) }
            persistSession(profileVisibleOverride = false)
            publishState()
        }
    }

    fun downloadIdentity() {
        val selection = currentSelection ?: return
        val export = selection.identity.protectedExport ?: return
        val fileBaseName = identityNameParts.toIdentityDownloadFileName()
        scope.launch {
            runCatching {
                transfer.download(export, fileBaseName)
            }.onFailure {
                logger.error("Identity download failed", it)
            }
        }
    }

    fun copyIdentityToClipboard() {
        val selection = currentSelection ?: return
        val export = selection.identity.protectedExport ?: return
        scope.launch {
            runCatching {
                transfer.copyToClipboard(export)
            }.onFailure {
                logger.error("Identity copy failed", it)
            }
        }
    }

    fun copyGidToClipboard() {
        val selection = currentSelection ?: return
        scope.launch {
            runCatching {
                transfer.copyTextToClipboard(selection.identity.gid)
            }.onFailure {
                logger.error("GID copy failed", it)
            }
        }
    }

    fun exportPostsCsv() {
        if (!isCurrentSelectionConnected) return
        val fileBaseName = identityNameParts.toIdentityDownloadFileName()
        runExclusive(
            failureMessage = "Posts export failed",
            beforeRun = {
                errorText = null
                statusText = textProvider.exportInProgress
                publishState()
            },
            onFailure = {
                statusText = null
                errorText = textProvider.exportError
                finishRestoring()
                publishState()
            },
        ) {
            postsExport.export(fileBaseName)
            statusText = null
            errorText = null
            publishState()
        }
    }

    fun freezePosts() {
        if (!isCurrentSelectionConnected) return
        runSnapshotAction(
            statusText = textProvider.freezeInProgress,
            errorText = textProvider.freezeError,
            successText = textProvider.freezeSuccess,
        ) {
            privacySnapshots.freeze("mosa-snapshot")
            null
        }
    }

    fun restorePosts() {
        if (!isCurrentSelectionConnected) return
        runSnapshotAction(
            statusText = textProvider.restoreInProgress,
            errorText = textProvider.restoreError,
            successTextProvider = { textProvider.restoreSuccess(it) },
        ) {
            privacySnapshots.restore()?.restoredCount
        }
    }

    fun uploadIdentity() {
        runExclusive(
            failureMessage = "Identity upload failed",
            onFailure = ::handleImportFailure,
        ) {
            val serialized = transfer.upload() ?: return@runExclusive
            val imported = importProtectedIdentity(serialized) ?: return@runExclusive
            val updated = repository.import(imported)
            applySelection(updated)
        }
    }

    fun pasteIdentityFromClipboard() {
        runExclusive(
            failureMessage = "Identity paste failed",
            onFailure = ::handleImportFailure,
        ) {
            val serialized = transfer.pasteFromClipboard()
            val imported = importProtectedIdentity(serialized) ?: return@runExclusive
            val updated = repository.import(imported)
            applySelection(updated)
        }
    }

    fun pasteIdentityFromContent(serialized: String) {
        runExclusive(
            failureMessage = "Identity paste failed",
            onFailure = ::handleImportFailure,
        ) {
            val imported = importProtectedIdentity(serialized) ?: return@runExclusive
            val updated = repository.import(imported)
            applySelection(updated)
        }
    }

    private suspend fun importProtectedIdentity(serialized: String): Identity? {
        val password = passwordPrompter.promptExistingPassword() ?: return null
        return runCatching { protector.import(serialized, password) }
            .getOrElse { error ->
                throw InvalidIdentityTransferException(error.message ?: "Identity parse failed", error)
            }
    }

    private suspend fun ensureProtectedSelection(): IdentitySelection? {
        val selection = currentSelection ?: return null
        if (selection.isPersisted) return selection
        val password = passwordPrompter.promptNewPassword() ?: return null
        val protected = repository.protectCurrent(password)
        currentSelection = protected
        return protected
    }

    private fun navigate(
        failureMessage: String,
        loader: suspend IdentitySelectionRepository.() -> IdentitySelection?,
    ) {
        runExclusive(failureMessage) {
            val selection = loader(repository) ?: return@runExclusive
            applySelection(selection)
        }
    }

    private suspend fun applySelection(
        selection: IdentitySelection,
        profileVisibleOverride: Boolean? = false,
    ) {
        currentSelection = selection
        val visuals = visualsProvider(selection.identity)
        identityNameParts = visuals.name
        identityAvatar = visuals.avatar
        errorText = null
        restoreConnection(selection)
        finishRestoring()
        publishState()
        persistSession(profileVisibleOverride = profileVisibleOverride)
    }

    private fun runSnapshotAction(
        statusText: String,
        errorText: String,
        successText: String? = null,
        successTextProvider: ((Int) -> String)? = null,
        action: suspend () -> Int?,
    ) {
        runExclusive(
            failureMessage = "Privacy snapshot action failed",
            beforeRun = {
                this.errorText = null
                this.statusText = statusText
                publishState()
            },
            onFailure = {
                this.statusText = null
                this.errorText = errorText
                finishRestoring()
                publishState()
            },
        ) {
            val restoredCount = action()
            if (restoredCount == null && successTextProvider != null) {
                this.statusText = null
                this.errorText = null
                publishState()
                return@runExclusive
            }
            snapshotRefreshVersion += 1
            this.statusText = successText ?: successTextProvider?.invoke(checkNotNull(restoredCount))
            this.errorText = null
            publishState()
        }
    }

    private fun setBusy(value: Boolean) {
        isBusy = value
        publishState()
    }

    private fun publishState() {
        if (isRestoring) return
        _state.value = if (connectedIdentityGid != null) connectedState() else profileViewState()
    }

    private fun finishRestoring() {
        if (!isRestoring) return
        isRestoring = false
    }

    private fun profileViewState(): IdentitySessionViewState.Profile =
        currentBinding().let { binding ->
        IdentitySessionViewState.Profile(
            identityGid = binding.displayedIdentityGid,
            connectedIdentityGid = binding.connectedIdentityGid,
            identityNameParts = identityNameParts,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = errorText,
            statusText = statusText,
            snapshotRefreshVersion = snapshotRefreshVersion,
            canDelete = currentSelection != null,
            canDeletePosts = binding.isDisplayedIdentityConnected,
            canExportPosts = binding.isDisplayedIdentityConnected,
            canFreezePosts = binding.isDisplayedIdentityConnected,
            canRestorePosts = binding.isDisplayedIdentityConnected,
            canNavigatePrevious = currentSelection?.hasPrevious ?: false,
            canNavigateNext = currentSelection != null,
            isShowingLastIdentity = currentSelection?.let { !it.hasNext } ?: false,
            identityIndex = currentSelection?.index ?: 0,
            identityTotal = currentSelection?.total ?: 1,
            canConnect = currentSelection != null,
            canDownload = currentSelection?.isPersisted == true,
            canCopy = currentSelection?.isPersisted == true,
            canUpload = true,
            canPaste = true,
            isPersisted = currentSelection?.isPersisted ?: false,
        )
    }

    private fun connectedState(): IdentitySessionViewState.Connected =
        IdentitySessionViewState.Connected(
            identityName = identityNameParts.toString(),
            profile = profileViewState(),
        )

    private fun runExclusive(
        failureMessage: String,
        beforeRun: () -> Unit = {},
        onFailure: (Throwable) -> Unit = ::handleFailure,
        block: suspend () -> Unit,
    ) {
        if (isBusy) return
        beforeRun()
        setBusy(true)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                block()
            } catch (error: Throwable) {
                logger.error(failureMessage, error)
                onFailure(error)
            } finally {
                setBusy(false)
            }
        }
    }

    private fun handleFailure(error: Throwable) {
        errorText = errorMessage(error)
        finishRestoring()
        publishState()
    }

    private fun handleImportFailure(error: Throwable) {
        if (error is InvalidIdentityTransferException) {
            errorText = textProvider.invalidIdentity
            finishRestoring()
            publishState()
            return
        }
        handleFailure(error)
    }

    private fun handlePreparationFailure(error: Throwable) {
        val message = errorMessage(error)
        currentSelection = null
        identityAvatar = null
        resetConnection()
        identityNameParts = placeholderName(textProvider.failedToPrepare(message))
        errorText = message
        finishRestoring()
        publishState()
    }

    private suspend fun restoreConnection(selection: IdentitySelection) {
        val snapshot = sessionStore.load()
        connectedIdentityGid = snapshot?.connectedIdentityGid
    }

    private suspend fun persistSession(
        profileVisibleOverride: Boolean? = null,
        preserveConnectedIdentity: Boolean = true,
    ) {
        val current = sessionStore.load()
        val connectedGid =
            when {
                connectedIdentityGid != null -> connectedIdentityGid
                preserveConnectedIdentity -> current?.connectedIdentityGid
                else -> null
            }
        val isProfileVisible: Boolean = profileVisibleOverride ?: current?.isProfileVisible ?: false
        sessionStore.save(
            IdentitySessionSnapshot(
                connectedIdentityGid = connectedGid,
                isProfileVisible = isProfileVisible,
                selectedTopicTitle = current?.selectedTopicTitle,
            )
        )
    }

    private fun resetConnection() {
        connectedIdentityGid = null
        scope.launch {
            persistSession(profileVisibleOverride = false, preserveConnectedIdentity = false)
        }
    }

    private val isCurrentSelectionConnected: Boolean
        get() = currentBinding().isDisplayedIdentityConnected

    private fun currentBinding(): IdentityBinding =
        IdentityBinding(
            displayedIdentityGid = currentSelection?.identity?.gid,
            connectedIdentityGid = connectedIdentityGid,
        )

    private fun errorMessage(error: Throwable): String {
        if (error is CancellationException) return textProvider.unknownError
        return error.message?.takeIf { it.isNotBlank() }
            ?: error::class.simpleName
            ?: textProvider.unknownError
    }
}
