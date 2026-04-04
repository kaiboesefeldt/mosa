package straightway.mosa.message

data class AdminMessageRecord(
    val id: String,
    val recipientGid: String,
    val senderGid: String,
    val createdAt: Long,
    val subject: String,
    val body: String,
    val expiresAt: Long,
    val deliveryKey: String = "",
)
