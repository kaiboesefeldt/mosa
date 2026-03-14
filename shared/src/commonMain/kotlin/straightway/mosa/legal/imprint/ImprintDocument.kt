package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintDocument(
    val provider: ImprintProviderBlock = ImprintProviderBlock(),
    val address: ImprintAddress = ImprintAddress(),
    val contact: ImprintContactBlock = ImprintContactBlock(),
    val register: ImprintRegisterBlock? = null,
    val tax: ImprintTaxBlock? = null,
    val supervision: ImprintSupervisionBlock? = null,
    val editorial: ImprintEditorialBlock? = null,
    val additionalMarkdown: String? = null,
)
