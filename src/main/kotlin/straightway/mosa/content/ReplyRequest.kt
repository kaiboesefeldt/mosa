package straightway.mosa.content

data class ReplyRequest(
    val content: PublishRequest,
    val parentHash: String,
)
