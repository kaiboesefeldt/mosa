package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificateResponse

interface PodCertificateStore {
    fun load(podBase: String, gid: String): PodCertificateResponse?
    fun store(podBase: String, gid: String, certificate: PodCertificateResponse)
    fun remove(podBase: String, gid: String)
}
