package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificate

interface PodCertificateStore {
    fun load(podBase: String, gid: String): PodCertificate?
    fun store(podBase: String, gid: String, certificate: PodCertificate)
    fun remove(podBase: String, gid: String)
}

