package straightway.mosa.client.content

interface TopicStore {
    suspend fun load(): List<Topic>
    suspend fun save(topics: List<Topic>)
}
