package straightway.mosa.identity.moderation

import kotlinx.serialization.Serializable

@Serializable
data class AdminIdentityModerationStatus(
    val isAdmin: Boolean,
    val isBlocked: Boolean,
)
