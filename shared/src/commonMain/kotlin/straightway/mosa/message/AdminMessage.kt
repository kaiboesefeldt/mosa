package straightway.mosa.message

import kotlinx.serialization.Serializable

@Serializable
data class AdminMessage(
    val id: String,
    val senderGid: String,
    val createdAt: Long,
    val subject: String,
    val body: String,
    val remainingLifetimeDays: Int = 0,
)
