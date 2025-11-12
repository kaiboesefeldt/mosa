package straightway.mosa.client.topic

interface TopicStore {
    suspend fun load(): List<Topic>
    suspend fun save(topics: List<Topic>)
}
