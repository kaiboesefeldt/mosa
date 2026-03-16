package straightway.mosa.client.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import react.useEffect
import react.useMemo
import react.useState
import straightway.mosa.client.content.ContentCapabilitiesService

data class IdentityAdminPresentation(
    val showsConnectedAdminBadge: Boolean,
    val showsDisplayedAdminBadge: Boolean,
    val canDeleteAsAdmin: Boolean,
    val avatarTitleKey: String,
)

fun presentIdentityAdminStatus(
    connectedIdentityIsAdmin: Boolean,
    displayedIdentityIsAdmin: Boolean,
): IdentityAdminPresentation =
    IdentityAdminPresentation(
        showsConnectedAdminBadge = connectedIdentityIsAdmin,
        showsDisplayedAdminBadge = displayedIdentityIsAdmin,
        canDeleteAsAdmin = connectedIdentityIsAdmin,
        avatarTitleKey =
            if (connectedIdentityIsAdmin) {
                "connected.profile.avatar.admin.title"
            } else {
                "connected.profile.avatar.title"
            },
    )

class IdentityAdminCapabilityLookup(
    private val isAdmin: suspend (String) -> Boolean,
) {
    private var requestVersion = 0

    suspend fun refresh(
        identityGid: String?,
        onResolved: (Boolean) -> Unit,
    ) {
        requestVersion += 1
        val activeRequestVersion = requestVersion
        if (identityGid == null) {
            onResolved(false)
            return
        }
        onResolved(false)
        val resolved = isAdmin(identityGid)
        if (requestVersion == activeRequestVersion) {
            onResolved(resolved)
        }
    }
}

fun useIdentityAdminPresentation(
    connectedIdentityGid: String?,
    displayedIdentityGid: String?,
    isProfileVisible: Boolean,
    contentCapabilitiesService: ContentCapabilitiesService,
    scope: CoroutineScope,
): IdentityAdminPresentation {
    val connectedLookup =
        useMemo(contentCapabilitiesService) {
            IdentityAdminCapabilityLookup(contentCapabilitiesService::isAdmin)
        }
    val displayedLookup =
        useMemo(contentCapabilitiesService) {
            IdentityAdminCapabilityLookup(contentCapabilitiesService::isAdmin)
        }
    var connectedIdentityIsAdmin by useState(false)
    var displayedIdentityIsAdmin by useState(false)

    useEffect(connectedIdentityGid) {
        scope.launch {
            connectedLookup.refresh(connectedIdentityGid) { connectedIdentityIsAdmin = it }
        }
    }
    useEffect(isProfileVisible, displayedIdentityGid) {
        scope.launch {
            displayedLookup.refresh(displayedIdentityGid.takeIf { isProfileVisible }) { displayedIdentityIsAdmin = it }
        }
    }

    return presentIdentityAdminStatus(
        connectedIdentityIsAdmin = connectedIdentityIsAdmin,
        displayedIdentityIsAdmin = displayedIdentityIsAdmin,
    )
}
