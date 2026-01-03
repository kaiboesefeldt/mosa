package straightway.mosa.auth

class ClientIpResolver(
    private val trustedProxyIps: Set<String>,
) {
    fun resolve(
        remoteHost: String,
        forwardedFor: String?,
    ): String {
        if (!trustedProxyIps.contains(remoteHost)) {
            return remoteHost
        }
        val firstForwarded =
            forwardedFor
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        return firstForwarded ?: remoteHost
    }
}
