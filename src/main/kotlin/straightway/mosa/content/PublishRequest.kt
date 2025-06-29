package straightway.mosa.content

data class PublishRequest(
    val authorGID: String,
    val text: String,
    val receivedAt: Long,
)
