package straightway.mosa.client

import kotlin.js.json

data class PodCertificate(
    val gid: String,
    val exp: Long, // epoch seconds
    val sig: String,
    val inf: String? = null,
) {
    fun toJson() = json(
        "gid" to gid,
        "exp" to exp.toInt(),
        "sig" to sig,
    ).apply {
        if (inf != null) this["inf"] = inf
    }

    companion object {
        operator fun invoke(dynCert: dynamic): PodCertificate =
            PodCertificate(
                gid = dynCert.gid as String,
                exp = (dynCert.exp as Number).toLong(),
                sig = dynCert.sig as String,
                inf = if (dynCert.hasOwnProperty("inf")) dynCert.inf as String? else null,
            )
    }
}
