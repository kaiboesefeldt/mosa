package straightway.mosa.auth

data class PodCertificate(
    val gid: String,
    val exp: Long,
    val sig: String,
    val inf: String? = null,
)

fun PodCertificate.toMap(): Map<String, Any> =
    mapOf(
        "gid" to gid,
        "exp" to exp,
        "sig" to sig,
    ) + if (inf === null) emptyMap() else mapOf<String, Any>("inf" to inf)

fun Map<String, Any>.toPodCertificate(): PodCertificate? {
    return PodCertificate(
        gid = (this["gid"] ?: return null).toString(),
        exp = (this["exp"] as? Number)?.toLong() ?: return null,
        sig = (this["sig"] ?: return null).toString(),
        inf = this["inf"]?.toString(),
    )
}
