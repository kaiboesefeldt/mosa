package straightway.mosa.auth

data class PodCertificate(
    val gid: String,
    val exp: Long,
    val sig: String,
    val inf: String? = null,
)
