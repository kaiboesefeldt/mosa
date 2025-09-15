package straightway.mosa.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticationApprovalRequest(
    val gid: String,
)
