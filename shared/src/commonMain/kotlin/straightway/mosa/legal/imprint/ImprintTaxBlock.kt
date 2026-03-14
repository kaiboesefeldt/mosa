package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintTaxBlock(
    val vatId: String? = null,
    val businessId: String? = null,
    val other: String? = null,
)
