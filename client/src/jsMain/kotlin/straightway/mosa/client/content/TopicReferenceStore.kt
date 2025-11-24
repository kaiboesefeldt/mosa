package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

class TopicReferenceStore {
    private val topics = mutableMapOf<String, TopicRecord>()

    fun load(topic: String): TopicRecord? = topics[topic]

    fun update(
        topic: String,
        entries: List<QueryResult.Entry>,
    ) {
        if (entries.isEmpty()) return
        val current = topics[topic]
        val merged = current?.entries?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        entries.forEach { entry ->
            if (entry.similarity.isFinite()) {
                merged[entry.id] = TopicEntryRef(entry.id, entry.similarity)
            }
        }
        val lastFetchedAt = entries.maxOfOrNull { it.storedAt } ?: current?.lastFetchedAt
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

    fun clear(topic: String) {
        topics.remove(topic)
    }
}

