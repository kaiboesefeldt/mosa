package straightway.mosa.auth

import kotlinx.serialization.Serializable

@Serializable
data class PodCertificateResponse(
    val certificate: PodCertificate,
    val refreshRecommendedAt: Long = 0,
)
