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
            statusText = null
            canDelete = true
            canConnect = true
            isPersisted = false
            canNavigatePrevious = false
            canNavigateNext = false
            isShowingLastIdentity = false
            identityIndex = 0
            identityTotal = 1
            canDeletePosts = false
            canExportPosts = false
            canFreezePosts = false
            canRestorePosts = false
            onConnect = {}
            canDownload = true
            onPrevious = {}
            onNext = {}
            onDelete = {}
            onDeletePosts = {}
            onExportPosts = {}
            onFreezePosts = {}
            onRestorePosts = {}
            onDownload = {}
            canUpload = true
            onUpload = {}
            canCopy = true
            onCopy = {}
            onCopyGid = {}
            canPaste = true
            onPaste = {}
            onPasteContent = {}
            onClose = null
            exitAnimation = ProfileExitAnimation.None
            entryAnimation = ProfileEntryAnimation.None
            configure()
        }
    )
