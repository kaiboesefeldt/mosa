package straightway.mosa.content

data class ReplyRequest(
    val content: PublishRequest,
    val parentId: String,
)
