package straightway.mosa.message

import kotlinx.serialization.Serializable

@Serializable
data class CreateAdminMessageRequest(
    val recipientGid: String,
    val subject: String,
    val body: String,
)
