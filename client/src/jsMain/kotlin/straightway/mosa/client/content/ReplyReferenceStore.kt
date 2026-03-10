package straightway.mosa.client.content

class ReplyReferenceStore {
    private val replies = mutableMapOf<String, ReplyRecord>()

    fun load(rootId: String): ReplyRecord? = replies[rootId]

    fun update(
        rootId: String,
        entries: List<ThreadScopedReply>,
    ) {
        if (entries.isEmpty()) return
        val current = replies[rootId]
        val merged = current?.entries?.associateBy { it.id }?.toMutableMap() ?: mutableMapOf()
        entries.forEach { entry ->
            if (entry.post.storedAt >= 0) {
                merged[entry.post.id] = ReplyEntryRef(entry.post.id, entry.post.storedAt)
            }
        }
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt } ?: current?.lastFetchedAt
        replies[rootId] =
            ReplyRecord(
                merged.values.sortedBy { it.storedAt },
                lastFetchedAt,
            )
    }

    fun removeMissing(
        rootId: String,
        missingIds: Collection<String>,
    ) {
        if (missingIds.isEmpty()) return
        val current = replies[rootId] ?: return
        val missing = missingIds.toSet()
        val filtered = current.entries.filterNot { missing.contains(it.id) }
        replies[rootId] = ReplyRecord(filtered, current.lastFetchedAt)
    }

    fun removePost(postId: String) {
        if (postId.isBlank()) return
        replies.entries.forEach { (rootId, record) ->
            val filtered = record.entries.filterNot { it.id == postId }
            if (filtered.size != record.entries.size) {
                replies[rootId] = ReplyRecord(filtered, record.lastFetchedAt)
            }
        }
    }

    fun clear(rootId: String) {
        replies.remove(rootId)
    }

    fun clearAll() {
        replies.clear()
    }
}
