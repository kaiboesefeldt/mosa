package straightway.mosa.client.ui

import straightway.mosa.identity.FictionalName

sealed interface IdentitySessionViewState {
    data class Profile(
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
        val canDownload: Boolean,
        val canUpload: Boolean,
    ) : IdentitySessionViewState

    data class Connected(
        val identityName: String,
        val profile: Profile,
    ) : IdentitySessionViewState
}
