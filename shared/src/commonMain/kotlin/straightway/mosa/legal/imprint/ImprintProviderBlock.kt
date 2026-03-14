package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintProviderBlock(
    val name: String? = null,
    val legalForm: String? = null,
    val representative: String? = null,
)
