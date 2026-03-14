package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintRegisterBlock(
    val court: String? = null,
    val number: String? = null,
    val name: String? = null,
)
