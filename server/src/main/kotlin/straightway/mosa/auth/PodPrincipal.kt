package straightway.mosa.auth

data class PodPrincipal(
    val certificate: PodCertificate,
) {
    val gid: String = certificate.gid
}
