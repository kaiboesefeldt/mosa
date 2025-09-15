package straightway.mosa.auth

import kotlinx.serialization.Serializable

@Serializable
data class PodCertificate(
    val gid: String,
    val exp: Long, // epoch seconds
    val sig: String,
    val inf: String? = null,
)
