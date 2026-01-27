package straightway.mosa

import straightway.mosa.auth.ClientIpResolver

data class AuthConfig(
    val tokenUtil: TokenUtil,
    val clientIpResolver: ClientIpResolver,
)
