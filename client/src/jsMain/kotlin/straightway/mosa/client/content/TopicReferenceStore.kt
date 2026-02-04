package straightway.mosa.client.content

class TopicReferenceStore {
    private val topics = mutableMapOf<String, TopicRecord>()

    fun load(topic: String): TopicRecord? = topics[topic]

    fun update(
        topic: String,
        entries: List<TopicScopedPost>,
    ) {
        if (entries.isEmpty()) return
        val current = topics[topic]
        val merged = current?.entries?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        entries.forEach { entry ->
            if (entry.similarity.isFinite()) {
                merged[entry.post.id] = TopicEntryRef(entry.post.id, entry.similarity)
            }
        }
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt } ?: current?.lastFetchedAt
        topics[topic] = TopicRecord(merged.values.toList(), lastFetchedAt)
    }

    fun removeMissing(
        topic: String,
        missingIds: Collection<String>,
    ) {
        if (missingIds.isEmpty()) return
        val current = topics[topic] ?: return
        val missing = missingIds.toSet()
        val filtered = current.entries.filterNot { missing.contains(it.id) }
        topics[topic] = TopicRecord(filtered, current.lastFetchedAt)
    }

    fun removePost(postId: String) {
        if (postId.isBlank()) return
        topics.entries.forEach { (topic, record) ->
            val filtered = record.entries.filterNot { it.id == postId }
            if (filtered.size != record.entries.size) {
                topics[topic] = TopicRecord(filtered, record.lastFetchedAt)
            }
        }
    }

    fun clear(topic: String) {
        topics.remove(topic)
    }
}
