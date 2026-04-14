package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class AdminIdentityModerationStatus(
    val isAdmin: Boolean,
    val isBlocked: Boolean,
)
