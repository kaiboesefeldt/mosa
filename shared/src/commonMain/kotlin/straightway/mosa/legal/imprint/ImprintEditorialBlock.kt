package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintEditorialBlock(
    val responsiblePerson: String? = null,
    val address: ImprintAddress? = null,
)
