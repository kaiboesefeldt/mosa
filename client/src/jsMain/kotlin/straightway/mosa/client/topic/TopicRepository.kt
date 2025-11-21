package straightway.mosa.client.topic

class TopicRepository(
    private val store: TopicStore,
    private val postCache: PostCache,
) {
    private var cache: List<Topic>? = null

    suspend fun all(): List<Topic> {
        val current = cache
        if (current != null) return current
        val loaded = store.load()
        cache = loaded
        return loaded
    }

    suspend fun add(title: String): List<Topic> {
        val sanitized = title.trim()
        if (sanitized.isEmpty()) return all()
        val updated = all() + Topic(sanitized)
        store.save(updated)
        cache = updated
        return updated
    }

    suspend fun remove(topic: Topic): List<Topic> {
        val current = all()
        val updated = current.filterNot { it.title == topic.title }
        if (updated.size == current.size) return current
        store.save(updated)
        postCache.clear(topic.title)
        cache = updated
        return updated
    }

    suspend fun applyFilters(filters: Map<String, Float>) {
        val sanitizedFilters = TopicFilters.sanitize(filters)
        val clearedTopics = mutableListOf<String>()
        val updated =
            all().map { topic ->
                val nextFilter = sanitizedFilters[topic.title]
                if (topic.scoreMustBeAbove == nextFilter) {
                    topic
                } else {
                    clearedTopics += topic.title
                    topic.copy(scoreMustBeAbove = nextFilter)
                }
            }
        store.save(updated)
        clearedTopics.forEach { topic -> postCache.clear(topic) }
        cache = updated
    }
}
