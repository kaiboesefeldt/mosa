package straightway.mosa.client

import kotlin.js.Date

data class PodCertificate(
    val gid: String,
    val exp: Date,
    val sig: String,
    val inf: String? = null,
) {
    companion object {
        operator fun invoke(dynCert: dynamic): PodCertificate =
            PodCertificate(
                gid = dynCert.gid as String,
                exp = Date((dynCert.exp as Number).toLong() * 1000),
                sig = dynCert.sig as String,
                inf = if (dynCert.hasOwnProperty("inf")) dynCert.inf as String? else null,
            )
    }
}

