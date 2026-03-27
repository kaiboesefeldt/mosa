package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import react.useEffect
import react.useRef
import react.useState

internal data class ProfileState(
    val isProfileVisible: Boolean,
    val profileEntryAnimation: ProfileEntryAnimation,
    val profileExitAnimation: ProfileExitAnimation,
    val toastMessage: String?,
)

internal data class ProfileActions(
    val showProfile: () -> Unit,
    val hideProfile: () -> Unit,
    val hideProfileWithSuction: () -> Unit,
    val showToast: (String) -> Unit,
)

internal data class ProfileController(
    val state: ProfileState,
    val actions: ProfileActions,
)

internal fun useProfileController(
    sessionStore: IdentitySessionStore,
    scope: CoroutineScope,
    isPersistedProfile: Boolean,
    persistProfileVisibilitySnapshot: suspend (Boolean) -> Unit,
): ProfileController {
    var isProfileVisible by useState(false)
    var profileEntryAnimation by useState(ProfileEntryAnimation.None)
    val profileEntryTimerRef = useRef<Int>()
    var profileExitAnimation by useState(ProfileExitAnimation.None)
    val profileExitTimerRef = useRef<Int>()
    var toastMessage by useState<String?>(null)
    val toastTimerRef = useRef<Int>()
    val profileAnimationDurationMs = 760

    fun clearToastTimer() {
        toastTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            toastTimerRef.current = null
        }
    }

    fun clearProfileEntryTimer() {
        profileEntryTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            profileEntryTimerRef.current = null
        }
    }

    fun clearProfileExitTimer() {
        profileExitTimerRef.current?.let { timerId ->
            window.clearTimeout(timerId)
            profileExitTimerRef.current = null
        }
    }

    fun showToast(message: String) {
        clearToastTimer()
        toastMessage = message
        val timerId =
            window.setTimeout({
                toastMessage = null
            }, 3200)
        toastTimerRef.current = timerId
    }

    fun startProfileEntryAnimation() {
        clearProfileExitTimer()
        profileExitAnimation = ProfileExitAnimation.None
        clearProfileEntryTimer()
        profileEntryAnimation = ProfileEntryAnimation.AvatarEmerge
        val timerId =
            window.setTimeout({
                profileEntryAnimation = ProfileEntryAnimation.None
            }, profileAnimationDurationMs)
        profileEntryTimerRef.current = timerId
    }

    fun persistProfileVisibility(visible: Boolean) {
        if (visible) {
            clearProfileExitTimer()
            profileExitAnimation = ProfileExitAnimation.None
        } else {
            clearProfileEntryTimer()
            profileEntryAnimation = ProfileEntryAnimation.None
            clearProfileExitTimer()
            profileExitAnimation = ProfileExitAnimation.None
        }
        isProfileVisible = visible
        scope.launch { persistProfileVisibilitySnapshot(visible) }
    }

    fun showProfile() {
        if (isProfileVisible) {
            if (!isPersistedProfile) {
                startProfileEntryAnimation()
            }
            return
        }
        startProfileEntryAnimation()
        persistProfileVisibility(true)
    }

    fun hideProfile() {
        clearProfileEntryTimer()
        profileEntryAnimation = ProfileEntryAnimation.None
        clearProfileExitTimer()
        profileExitAnimation = ProfileExitAnimation.None
        persistProfileVisibility(false)
    }

    fun hideProfileWithSuction() {
        if (!isProfileVisible) return
        if (profileExitAnimation == ProfileExitAnimation.AvatarSuction) return
        clearProfileExitTimer()
        clearProfileEntryTimer()
        profileEntryAnimation = ProfileEntryAnimation.None
        profileExitAnimation = ProfileExitAnimation.AvatarSuction
        val timerId =
            window.setTimeout({
                hideProfile()
            }, profileAnimationDurationMs)
        profileExitTimerRef.current = timerId
    }

    useEffect(sessionStore) {
        scope.launch {
            val snapshot = runCatching { sessionStore.load() }.getOrNull()
            isProfileVisible = snapshot?.isProfileVisible ?: false
        }
    }

    useEffect(*emptyArray<dynamic>()) {
        try {
            awaitCancellation()
        } finally {
            clearProfileExitTimer()
            clearProfileEntryTimer()
            clearToastTimer()
        }
    }

    return ProfileController(
        state =
            ProfileState(
                isProfileVisible = isProfileVisible,
                profileEntryAnimation = profileEntryAnimation,
                profileExitAnimation = profileExitAnimation,
                toastMessage = toastMessage,
            ),
        actions =
            ProfileActions(
                showProfile = ::showProfile,
                hideProfile = ::hideProfile,
                hideProfileWithSuction = ::hideProfileWithSuction,
                showToast = ::showToast,
            ),
    )
}
