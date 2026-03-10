package straightway.mosa.client.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import react.useEffect
import react.useMemo
import react.useState
import straightway.mosa.client.avatar.DiceBearAvatarGenerator
import straightway.mosa.client.content.PostsExportPort
import straightway.mosa.client.content.PrivacySnapshotPort
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.IdentityProtector
import straightway.mosa.client.identity.PasswordPrompter
import straightway.mosa.client.logging.Logger
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator

sealed interface IdentityScreen {
    data object Loading : IdentityScreen

    data class Profile(
        val identityNameParts: FictionalName,
        val identityAvatar: String?,
        val isBusy: Boolean,
        val errorText: String?,
        val statusText: String?,
        val snapshotRefreshVersion: Int,
        val canDelete: Boolean,
        val canDeletePosts: Boolean,
        val canExportPosts: Boolean,
        val canFreezePosts: Boolean,
        val canRestorePosts: Boolean,
        val canNavigatePrevious: Boolean,
        val canNavigateNext: Boolean,
        val isShowingLastIdentity: Boolean,
        val identityIndex: Int,
        val identityTotal: Int,
        val canConnect: Boolean,
        val isPersisted: Boolean,
        val onConnect: () -> Unit,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
        val onDelete: () -> Unit,
        val onDeletePosts: () -> Unit,
        val onExportPosts: () -> Unit,
        val onFreezePosts: () -> Unit,
        val onRestorePosts: () -> Unit,
        val onSelect: (Int) -> Unit,
        val canDownload: Boolean,
        val onDownload: () -> Unit,
        val canCopy: Boolean,
        val onCopy: () -> Unit,
        val canUpload: Boolean,
        val onUpload: () -> Unit,
        val canPaste: Boolean,
        val onPaste: () -> Unit,
        val onPasteContent: (String) -> Unit,
    ) : IdentityScreen

    data class Connected(
        val identityName: String,
        val profile: Profile,
    ) : IdentityScreen
}

fun useIdentitySession(): IdentityScreen {
    val translator = ClientInjector.get<Translator>()
    val repository = ClientInjector.get<IdentitySelectionRepository>()
    val transfer = ClientInjector.get<IdentityTransferPort>()
    val postsExport = ClientInjector.get<PostsExportPort>()
    val privacySnapshots = ClientInjector.get<PrivacySnapshotPort>()
    val protector = ClientInjector.get<IdentityProtector>()
    val passwordPrompter = ClientInjector.get<PasswordPrompter>()
    val avatarGenerator = ClientInjector.get<DiceBearAvatarGenerator>()
    val nameGenerator = ClientInjector.get<FictionalNameGenerator>()
    val textProvider =
        useMemo(translator) {
            TranslatorIdentitySessionTextProvider(translator)
        }
    val visualsFactory =
        useMemo(nameGenerator, avatarGenerator) {
            IdentityVisualsFactory(nameGenerator, avatarGenerator)
        }
    val scope = useMemo(*noDependencies) { MainScope() }
    val logger = useMemo(*noDependencies) { ClientInjector.get<Logger>() }
    val sessionStore = useMemo(*noDependencies) { ClientInjector.get<IdentitySessionStore>() }
    val machine =
        useMemo(repository, transfer, postsExport, privacySnapshots, protector, passwordPrompter, visualsFactory, logger, textProvider, sessionStore) {
            identitySessionStateMachineFactory(
                IdentitySessionMachineConfig(
                    repository = repository,
                    transfer = transfer,
                    postsExport = postsExport,
                    privacySnapshots = privacySnapshots,
                    protector = protector,
                    passwordPrompter = passwordPrompter,
                    textProvider = textProvider,
                    visualsFactory = visualsFactory,
                    scope = scope,
                    logger = logger,
                    sessionStore = sessionStore,
                ),
            )
        }
    val (viewState, setViewState) = useState(machine.state.value)

    useEffect(*noDependencies) {
        val job = scope.launch {
            machine.state.collect { setViewState(it) }
        }
        machine.refresh()
        try {
            awaitCancellation()
        } finally {
            job.cancel()
            scope.cancel()
        }
    }

    fun IdentitySessionViewState.Profile.toScreen(): IdentityScreen.Profile =
        IdentityScreen.Profile(
            identityNameParts = identityNameParts,
            identityAvatar = identityAvatar,
            isBusy = isBusy,
            errorText = errorText,
            statusText = statusText,
            snapshotRefreshVersion = snapshotRefreshVersion,
            canDelete = canDelete,
            canDeletePosts = canDeletePosts,
            canExportPosts = canExportPosts,
            canFreezePosts = canFreezePosts,
            canRestorePosts = canRestorePosts,
            canNavigatePrevious = canNavigatePrevious,
            canNavigateNext = canNavigateNext,
            isShowingLastIdentity = isShowingLastIdentity,
            identityIndex = identityIndex,
            identityTotal = identityTotal,
            canConnect = canConnect,
            isPersisted = isPersisted,
            onConnect = { machine.connectToPod() },
            onPrevious = { machine.navigateToPrevious() },
            onNext = { machine.navigateToNext() },
            onDelete = { machine.deleteIdentity() },
            onDeletePosts = {},
            onExportPosts = { machine.exportPostsCsv() },
            onFreezePosts = { machine.freezePosts() },
            onRestorePosts = { machine.restorePosts() },
            onSelect = { index -> machine.select(index) },
            canDownload = canDownload,
            onDownload = { machine.downloadIdentity() },
            canCopy = canCopy,
            onCopy = { machine.copyIdentityToClipboard() },
            canUpload = canUpload,
            onUpload = { machine.uploadIdentity() },
            canPaste = canPaste,
            onPaste = { machine.pasteIdentityFromClipboard() },
            onPasteContent = { content -> machine.pasteIdentityFromContent(content) },
        )

    return when (viewState) {
        is IdentitySessionViewState.Connected ->
            IdentityScreen.Connected(
                identityName = viewState.identityName,
                profile = viewState.profile.toScreen(),
            )
        is IdentitySessionViewState.Profile ->
            viewState.toScreen()
        IdentitySessionViewState.Loading ->
            IdentityScreen.Loading
    }
}

private object ClientInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

internal data class IdentitySessionMachineConfig(
    val repository: IdentitySelectionRepository,
    val transfer: IdentityTransferPort,
    val postsExport: PostsExportPort,
    val privacySnapshots: PrivacySnapshotPort,
    val protector: IdentityProtector,
    val passwordPrompter: PasswordPrompter,
    val textProvider: IdentitySessionTextProvider,
    val visualsFactory: IdentityVisualsFactory,
    val scope: CoroutineScope,
    val logger: Logger,
    val sessionStore: IdentitySessionStore,
)

internal typealias IdentitySessionStateMachineFactory = (IdentitySessionMachineConfig) -> IdentitySessionStateMachine

internal var identitySessionStateMachineFactory: IdentitySessionStateMachineFactory =
    { config ->
        IdentitySessionStateMachine(
            repository = config.repository,
            transfer = config.transfer,
            postsExport = config.postsExport,
            privacySnapshots = config.privacySnapshots,
            protector = config.protector,
            passwordPrompter = config.passwordPrompter,
            textProvider = config.textProvider,
            visualsProvider = { identity -> config.visualsFactory.create(identity) },
            scope = config.scope,
            logger = config.logger,
            sessionStore = config.sessionStore,
        )
    }
