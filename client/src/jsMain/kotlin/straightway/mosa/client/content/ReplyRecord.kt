package straightway.mosa.client.content

data class ReplyRecord(
    val entries: List<ReplyEntryRef>,
    val lastFetchedAt: Long?,
)
