package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class ContentCapabilitiesResult(
    val isAdmin: Boolean,
    val isBlocked: Boolean = false,
)
