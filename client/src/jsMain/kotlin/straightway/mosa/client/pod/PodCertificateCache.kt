package straightway.mosa.client.pod

import straightway.mosa.auth.PodCertificate
import straightway.mosa.client.aux.TimeService

class PodCertificateCache(
    private val timeService: TimeService,
) {
    fun cached(
        podBase: String,
        gid: String,
    ): PodCertificate? {
        val key = CacheKey(podBase, gid)
        val certificate = certificates[key] ?: return null
        if (isValid(certificate)) return certificate
        certificates.remove(key)
        return null
    }

    fun store(
        podBase: String,
        gid: String,
        certificate: PodCertificate,
    ) {
        certificates[CacheKey(podBase, gid)] = certificate
    }

    fun invalidate(
        podBase: String,
        gid: String,
    ) {
        certificates.remove(CacheKey(podBase, gid))
    }

    private fun isValid(certificate: PodCertificate): Boolean = timeService.now < certificate.exp

    private data class CacheKey(
        val podBase: String,
        val gid: String,
    )

    private val certificates = mutableMapOf<CacheKey, PodCertificate>()
}
