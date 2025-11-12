package straightway.mosa.client.ui

import kotlinx.browser.window
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
import straightway.mosa.client.i18n.Translator
import straightway.mosa.client.identity.IdentitySelectionRepository
import straightway.mosa.client.identity.IdentityTransferPort
import straightway.mosa.client.identity.MisJwtService
import straightway.mosa.client.logging.Logger
import straightway.mosa.client.pod.PodAccess
import straightway.mosa.client.pod.podAccess
import straightway.mosa.identity.FictionalName
import straightway.mosa.identity.FictionalNameGenerator

sealed interface IdentityScreen {
    data class Landing(
        val identityNameParts: FictionalName,
        val identityAvatar: String?,
        val isBusy: Boolean,
        val errorText: String?,
        val canDelete: Boolean,
        val canNavigatePrevious: Boolean,
        val canNavigateNext: Boolean,
        val isShowingLastIdentity: Boolean,
        val identityIndex: Int,
        val identityTotal: Int,
        val canConnect: Boolean,
        val onConnect: () -> Unit,
        val onPrevious: () -> Unit,
        val onNext: () -> Unit,
        val onDelete: () -> Unit,
        val onSelect: (Int) -> Unit,
        val canDownload: Boolean,
        val onDownload: () -> Unit,
        val canUpload: Boolean,
        val onUpload: () -> Unit,
    ) : IdentityScreen

    data class Connected(
        val identityName: String,
        val misJwt: String,
        val queryPreview: String?,
    ) : IdentityScreen
}

fun useIdentitySession(): IdentityScreen {
    val translator = ClientInjector.get<Translator>()
    val repository = ClientInjector.get<IdentitySelectionRepository>()
    val transfer = ClientInjector.get<IdentityTransferPort>()
    val misJwtService = ClientInjector.get<MisJwtService>()
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
    val defaultPod = (window.asDynamic().MOSA_POD_BASE as? String).orEmpty()

    val scope = useMemo(*noDependencies) { MainScope() }
    val logger = useMemo(*noDependencies) { ClientInjector.get<Logger>() }
    val machine =
        useMemo(
            repository,
            transfer,
            misJwtService,
            visualsFactory,
            defaultPod,
            logger,
            textProvider,
        ) {
            identitySessionStateMachineFactory(
                IdentitySessionMachineConfig(
                    repository = repository,
                    transfer = transfer,
                    misJwtService = misJwtService,
                    podAccessProvider = { host -> ClientInjector.podAccess(host) },
                    textProvider = textProvider,
                    visualsFactory = visualsFactory,
                    defaultPod = defaultPod,
                    scope = scope,
                    logger = logger,
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

    return when (viewState) {
        is IdentitySessionViewState.Connected ->
            IdentityScreen.Connected(
                identityName = viewState.identityName,
                misJwt = viewState.misJwt,
                queryPreview = viewState.queryPreview,
            )
        is IdentitySessionViewState.Landing ->
            IdentityScreen.Landing(
                identityNameParts = viewState.identityNameParts,
                identityAvatar = viewState.identityAvatar,
                isBusy = viewState.isBusy,
                errorText = viewState.errorText,
                canDelete = viewState.canDelete,
                canNavigatePrevious = viewState.canNavigatePrevious,
                canNavigateNext = viewState.canNavigateNext,
                isShowingLastIdentity = viewState.isShowingLastIdentity,
                identityIndex = viewState.identityIndex,
                identityTotal = viewState.identityTotal,
                canConnect = viewState.canConnect,
                onConnect = { machine.connectToPod() },
                onPrevious = { machine.navigateToPrevious() },
                onNext = { machine.navigateToNext() },
                onDelete = { machine.deleteIdentity() },
                onSelect = { index -> machine.select(index) },
                canDownload = viewState.canDownload,
                onDownload = { machine.downloadIdentity() },
                canUpload = viewState.canUpload,
                onUpload = { machine.uploadIdentity() },
            )
    }
}

private object ClientInjector : KoinComponent

private val noDependencies: Array<dynamic> = emptyArray()

internal data class IdentitySessionMachineConfig(
    val repository: IdentitySelectionRepository,
    val transfer: IdentityTransferPort,
    val misJwtService: MisJwtService,
    val podAccessProvider: (String) -> PodAccess,
    val textProvider: IdentitySessionTextProvider,
    val visualsFactory: IdentityVisualsFactory,
    val defaultPod: String,
    val scope: CoroutineScope,
    val logger: Logger,
)

internal typealias IdentitySessionStateMachineFactory = (IdentitySessionMachineConfig) -> IdentitySessionStateMachine

internal var identitySessionStateMachineFactory: IdentitySessionStateMachineFactory =
    { config ->
        IdentitySessionStateMachine(
            repository = config.repository,
            transfer = config.transfer,
            misJwtBuilder = { identity, certificate -> config.misJwtService.buildMisJwt(identity, certificate) },
            podAccessFactory = config.podAccessProvider,
            textProvider = config.textProvider,
            visualsProvider = { identity -> config.visualsFactory.create(identity) },
            defaultPod = config.defaultPod,
            scope = config.scope,
            logger = config.logger,
        )
    }
