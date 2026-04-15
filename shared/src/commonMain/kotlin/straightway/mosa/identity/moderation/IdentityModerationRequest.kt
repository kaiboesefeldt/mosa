package straightway.mosa.identity.moderation

import kotlinx.serialization.Serializable

@Serializable
data class IdentityModerationRequest(
    val gid: String,
)
