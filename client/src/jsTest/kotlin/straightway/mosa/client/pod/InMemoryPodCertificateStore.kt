package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificate

class InMemoryPodCertificateStore : PodCertificateStore {
    private val items = mutableMapOf<String, PodCertificate>()

    override fun load(podBase: String, gid: String): PodCertificate? = items[key(podBase, gid)]

    override fun store(podBase: String, gid: String, certificate: PodCertificate) {
        items[key(podBase, gid)] = certificate
    }

    override fun remove(podBase: String, gid: String) {
        items.remove(key(podBase, gid))
    }

    private fun key(podBase: String, gid: String): String = "$podBase|$gid"
}
