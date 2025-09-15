package straightway.mosa.auth

fun PodCertificate.toMap(): Map<String, Any> =
    mapOf(
        "gid" to gid,
        "exp" to exp,
        "sig" to sig,
    ) + (inf?.let { mapOf<String, Any>("inf" to it) } ?: emptyMap())

fun Map<String, Any>.toPodCertificate(): PodCertificate? {
    return PodCertificate(
        gid = (this["gid"] ?: return null).toString(),
        exp = (this["exp"] as? Number)?.toLong() ?: return null,
        sig = (this["sig"] ?: return null).toString(),
        inf = this["inf"]?.toString(),
    )
}
