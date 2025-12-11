package straightway.mosa.client.ui

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.InvalidIdentityTransferException
import straightway.mosa.client.logging.Logger

class IdentitySessionStateMachine(
    private val repository: IdentitySelectionRepository,
    private val transfer: IdentityTransferPort,
    private val textProvider: IdentitySessionTextProvider,
    private val visualsProvider: (Identity) -> IdentityVisuals,
    private val scope: CoroutineScope,
    private val logger: Logger,
    private val sessionStore: IdentitySessionStore,
) {
    private var currentSelection: IdentitySelection? = null
    private var identityNameParts = placeholderName(textProvider.loading)
    private var identityAvatar: String? = null
    private var isBusy = false
    private var errorText: String? = null
    private var isConnected = false
    private var isRestoring = true

    private val _state = MutableStateFlow<IdentitySessionViewState>(IdentitySessionViewState.Loading)
    val state: StateFlow<IdentitySessionViewState> = _state

    init {
        scope.launch {
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            val wasConnected = snapshot?.connectedIdentityGid != null
            if (wasConnected) {
                isConnected = true
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
        if (currentSelection == null || isBusy) return
        isConnected = true
        errorText = null
        scope.launch {
            runCatching { repository.persist() }
                .onFailure { logger.error("Identity persistence failed", it) }
                .onSuccess {
                    currentSelection = currentSelection?.copy(isPersisted = true)
                    publishState()
                }
            persistSession(profileVisibleOverride = false)
        }
        publishState()
    }

    fun downloadIdentity() {
        val selection = currentSelection ?: return
        val fileBaseName = identityNameParts.toIdentityDownloadFileName()
        scope.launch {
            runCatching {
                transfer.download(selection.identity, fileBaseName)
            }.onFailure {
                logger.error("Identity download failed", it)
            }
        }
    }

    fun copyIdentityToClipboard() {
        val selection = currentSelection ?: return
        scope.launch {
            runCatching {
                transfer.copyToClipboard(selection.identity)
            }.onFailure {
                logger.error("Identity copy failed", it)
            }
        }
    }

    fun uploadIdentity() {
        runExclusive(
            failureMessage = "Identity upload failed",
            onFailure = ::handleImportFailure,
        ) {
            val imported = transfer.upload() ?: return@runExclusive
            val updated = repository.import(imported)
            applySelection(updated)
        }
    }

    fun pasteIdentityFromClipboard() {
        runExclusive(
            failureMessage = "Identity paste failed",
            onFailure = ::handleImportFailure,
        ) {
            val imported = transfer.pasteFromClipboard()
            val updated = repository.import(imported)
            applySelection(updated)
        }
    }

    fun pasteIdentityFromContent(serialized: String) {
        runExclusive(
            failureMessage = "Identity paste failed",
            onFailure = ::handleImportFailure,
        ) {
            val imported = transfer.decode(serialized)
            val updated = repository.import(imported)
            applySelection(updated)
        }
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

    private fun setBusy(value: Boolean) {
        isBusy = value
        publishState()
    }

    private fun publishState() {
        if (isRestoring) return
        _state.value = if (isConnected) connectedState() else profileViewState()
    }

    private fun finishRestoring() {
        if (!isRestoring) return
        isRestoring = false
    }

    private fun profileViewState(): IdentitySessionViewState.Profile =
        IdentitySessionViewState.Profile(
            identityNameParts = identityNameParts,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = errorText,
            canDelete = currentSelection != null,
            canNavigatePrevious = currentSelection?.hasPrevious ?: false,
            canNavigateNext = currentSelection != null,
            isShowingLastIdentity = currentSelection?.let { !it.hasNext } ?: false,
            identityIndex = currentSelection?.index ?: 0,
            identityTotal = currentSelection?.total ?: 1,
            canConnect = currentSelection != null,
            canDownload = currentSelection != null,
            canCopy = currentSelection != null,
            canUpload = true,
            canPaste = true,
            isPersisted = currentSelection?.isPersisted ?: false,
        )

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
        isConnected = snapshot?.connectedIdentityGid == selection.identity.gid
    }

    private suspend fun persistSession(profileVisibleOverride: Boolean? = null) {
        val connectedGid = currentSelection?.identity?.gid?.takeIf { isConnected }
        val current = sessionStore.load()
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
        isConnected = false
        scope.launch {
            persistSession(profileVisibleOverride = false)
        }
    }

    private fun errorMessage(error: Throwable): String {
        if (error is CancellationException) return textProvider.unknownError
        return error.message?.takeIf { it.isNotBlank() }
            ?: error::class.simpleName
            ?: textProvider.unknownError
    }
}
