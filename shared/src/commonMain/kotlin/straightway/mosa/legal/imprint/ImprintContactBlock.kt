package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintContactBlock(
    val email: String? = null,
    val phone: String? = null,
    val website: String? = null,
)
