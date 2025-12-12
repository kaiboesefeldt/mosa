package straightway.mosa.client.ui

import react.create
import straightway.mosa.client.AppModule
import straightway.mosa.client.testing.RenderResult
import straightway.mosa.client.testing.render
import straightway.mosa.identity.FictionalName

internal fun renderIdentityProfileView(
    configure: IdentityProfileViewProps.() -> Unit = {},
): RenderResult =
    render(
        IdentityProfileView.create {
            AppModule.start()
            identityNameParts = FictionalName(
                givenNames = listOf("Default"),
                sureName = "Identity",
            )
            identityAvatar = null
            isBusy = false
            errorText = null
            canDelete = true
            canConnect = true
            isPersisted = false
            canNavigatePrevious = false
            canNavigateNext = false
            isShowingLastIdentity = false
            identityIndex = 0
            identityTotal = 1
            onConnect = {}
            canDownload = true
            onPrevious = {}
            onNext = {}
            onDelete = {}
            onDownload = {}
            canUpload = true
            onUpload = {}
            canCopy = true
            onCopy = {}
            canPaste = true
            onPaste = {}
            onPasteContent = {}
            onClose = null
            exitAnimation = ProfileExitAnimation.None
            entryAnimation = ProfileEntryAnimation.None
            configure()
        }
    )
