package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.client.aux.TimeService

class PodCertificateCache(
    private val timeService: TimeService,
    private val store: PodCertificateStore,
) {
    fun cached(
        podBase: String,
        gid: String,
    ): PodCertificateResponse? {
        val key = CacheKey(podBase, gid)
        val certificate = certificates[key] ?: store.load(podBase, gid) ?: return null
        if (isValid(certificate)) {
            certificates[key] = certificate
            return certificate
        }
        certificates.remove(key)
        store.remove(podBase, gid)
        return null
    }

    fun store(
        podBase: String,
        gid: String,
        certificate: PodCertificateResponse,
    ) {
        certificates[CacheKey(podBase, gid)] = certificate
        store.store(podBase, gid, certificate)
    }

    fun invalidate(
        podBase: String,
        gid: String,
    ) {
        certificates.remove(CacheKey(podBase, gid))
        store.remove(podBase, gid)
    }

    private fun isValid(certificate: PodCertificateResponse): Boolean = timeService.now < certificate.certificate.exp

    private data class CacheKey(
        val podBase: String,
        val gid: String,
    )

    private val certificates = mutableMapOf<CacheKey, PodCertificateResponse>()
}
