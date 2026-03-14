package straightway.mosa.legal.imprint

import kotlinx.serialization.Serializable

@Serializable
data class ImprintAddress(
    val street: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
)
