package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class IdentityModerationRequest(
    val gid: String,
)
