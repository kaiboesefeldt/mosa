package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificateResponse

class InMemoryPodCertificateStore : PodCertificateStore {
    private val items = mutableMapOf<String, PodCertificateResponse>()

    override fun load(podBase: String, gid: String): PodCertificateResponse? = items[key(podBase, gid)]

    override fun store(podBase: String, gid: String, certificate: PodCertificateResponse) {
        items[key(podBase, gid)] = certificate
    }

    override fun remove(podBase: String, gid: String) {
        items.remove(key(podBase, gid))
    }

    private fun key(podBase: String, gid: String): String = "$podBase|$gid"
}
