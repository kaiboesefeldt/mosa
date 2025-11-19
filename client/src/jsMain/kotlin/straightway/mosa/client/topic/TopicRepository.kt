package straightway.mosa.client.topic

class TopicRepository(
    private val store: TopicStore,
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
        val updated = all().filterNot { it.title == topic.title }
        store.save(updated)
        cache = updated
        return updated
    }

    suspend fun applyFilters(filters: Map<String, Float>) {
        val sanitizedFilters = TopicFilters.sanitize(filters)
        val updated =
            all().map { topic ->
                val nextFilter = sanitizedFilters[topic.title]
                if (topic.scoreMustBeAbove == nextFilter) {
                    topic
                } else {
                    topic.copy(scoreMustBeAbove = nextFilter)
                }
            }
        store.save(updated)
        cache = updated
    }
}
