package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintSupervisionBlock(
    val authority: String? = null,
    val address: ImprintAddress? = null,
    val website: String? = null,
)
