package straightway.mosa.client.ui

import straightway.mosa.identity.FictionalName

sealed interface IdentitySessionViewState {
    data object Loading : IdentitySessionViewState

    data class Profile(
        val identityNameParts: FictionalName,
        val identityAvatar: String?,
        val isBusy: Boolean,
        val errorText: String?,
        val statusText: String?,
        val canDelete: Boolean,
        val canDeletePosts: Boolean,
        val canExportPosts: Boolean,
        val canNavigatePrevious: Boolean,
        val canNavigateNext: Boolean,
        val isShowingLastIdentity: Boolean,
        val identityIndex: Int,
        val identityTotal: Int,
        val canConnect: Boolean,
        val canDownload: Boolean,
        val canCopy: Boolean,
        val canUpload: Boolean,
        val canPaste: Boolean,
        val isPersisted: Boolean,
    ) : IdentitySessionViewState

    data class Connected(
        val identityName: String,
        val profile: Profile,
    ) : IdentitySessionViewState
}
