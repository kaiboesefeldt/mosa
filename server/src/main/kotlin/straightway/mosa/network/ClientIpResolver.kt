package straightway.mosa.network

class ClientIpResolver(
    private val trustedProxyIps: Set<String>,
) {
    fun isTrustedProxy(remoteHost: String): Boolean = trustedProxyIps.contains(remoteHost)

    fun resolve(
        remoteHost: String,
        forwardedFor: String?,
    ): String {
        if (!isTrustedProxy(remoteHost)) {
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
