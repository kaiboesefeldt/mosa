package straightway.mosa.client.content

data class TopicRecord(
    val entries: List<TopicEntryRef>,
    val lastFetchedAt: Long?,
)