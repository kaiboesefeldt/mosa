package straightway.mosa.client.ui

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import straightway.mosa.client.identity.Identity
import straightway.mosa.client.identity.IdentitySelection
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
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

    private val _state = MutableStateFlow<IdentitySessionViewState>(initialViewState())
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

    fun uploadIdentity() {
        runExclusive("Identity upload failed") {
            val imported = transfer.upload() ?: return@runExclusive
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
        publishState()
        persistSession(profileVisibleOverride = profileVisibleOverride)
    }

    private fun setBusy(value: Boolean) {
        isBusy = value
        publishState()
    }

    private fun publishState() {
        _state.value = if (isConnected) connectedState() else profileViewState()
    }

    private fun initialViewState(): IdentitySessionViewState =
        if (isConnected) connectedState() else initialProfileState()

    private fun initialProfileState(): IdentitySessionViewState.Profile =
        IdentitySessionViewState.Profile(
            identityNameParts = identityNameParts,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = null,
            canDelete = false,
            canNavigatePrevious = false,
            canNavigateNext = false,
            isShowingLastIdentity = false,
            identityIndex = 0,
            identityTotal = 1,
            canConnect = false,
            canDownload = false,
            canUpload = true,
        )

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
            canUpload = true,
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
        scope.launch {
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
        publishState()
    }

    private fun handlePreparationFailure(error: Throwable) {
        val message = errorMessage(error)
        currentSelection = null
        identityAvatar = null
        resetConnection()
        identityNameParts = placeholderName(textProvider.failedToPrepare(message))
        errorText = message
        publishState()
    }

    private suspend fun restoreConnection(selection: IdentitySelection) {
        val snapshot = sessionStore.load()
        isConnected = snapshot?.connectedIdentityGid == selection.identity.gid
    }

    private suspend fun persistSession(profileVisibleOverride: Boolean? = null) {
        val connectedGid = currentSelection?.identity?.gid?.takeIf { isConnected }
        val current = sessionStore.load()
        val isProfileVisible = profileVisibleOverride ?: current?.isProfileVisible ?: false
        sessionStore.save(IdentitySessionSnapshot(connectedGid, isProfileVisible))
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
