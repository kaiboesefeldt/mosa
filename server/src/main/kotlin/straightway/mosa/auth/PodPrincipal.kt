package straightway.mosa.auth

data class PodPrincipal(
    val certificate: PodCertificate,
    val publicKeyBase64: String,
) {
    val gid: String = certificate.gid
}
