package straightway.mosa.client.topic

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
import straightway.mosa.content.QueryResult

internal const val topicPostCacheDatabaseName = "mosa-topic-post-cache"
internal const val topicPostTopicsDatabaseName = "mosa-topic-post-cache-topics"
internal const val topicPostEntriesStoreName = "topic-post-cache-entries"
internal const val topicPostTopicsStoreName = "topic-post-cache-topics"
internal const val topicPostStoredAtIndexName = "stored-at-index"
internal const val topicPostSchemaVersion: Short = 1
internal const val topicPostEvictionRatio = 0.05

data class TopicPostCacheSnapshot(
    val entries: List<QueryResult.Entry>,
    val lastFetchedAt: Long?,
)

interface TopicPostCache {
    suspend fun snapshot(topic: String): TopicPostCacheSnapshot
    suspend fun append(topic: String, entries: List<QueryResult.Entry>): TopicPostCacheSnapshot
    suspend fun clear(topic: String)
}

internal class IndexedDbTopicPostCache(
    private val entryStore: TopicEntryStore,
    private val topicStore: TopicReferenceStore,
    private val logger: Logger,
) : TopicPostCache {
    override suspend fun snapshot(topic: String): TopicPostCacheSnapshot =
        runCatching { snapshotInternal(topic) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache snapshot failed for $topic",
            ) {
                TopicPostCacheSnapshot(emptyList(), null)
            }

    override suspend fun append(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): TopicPostCacheSnapshot =
        runCatching { appendInternal(topic, entries) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache append failed for $topic",
            ) {
                fallbackSnapshot(entries)
            }

    override suspend fun clear(topic: String) {
        runCatching { topicStore.clear(topic) }
            .onFailureLogOrThrowCancellation(logger, "IndexedDB topic cache clear failed for $topic")
    }

    private suspend fun snapshotInternal(topic: String): TopicPostCacheSnapshot {
        val record = topicStore.load(topic) ?: return TopicPostCacheSnapshot(emptyList(), null)
        val resolution = entryStore.resolve(record.entries)
        if (resolution.missingIds.isNotEmpty()) {
            topicStore.removeMissing(topic, resolution.missingIds)
        }
        val sorted = resolution.resolved.sortedByDescending { it.similarity }
        return TopicPostCacheSnapshot(sorted, record.lastFetchedAt)
    }

    private suspend fun appendInternal(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): TopicPostCacheSnapshot {
        if (entries.isEmpty()) return snapshotInternal(topic)
        val persistable = entries.filter { it.id.isNotBlank() }
        if (persistable.isEmpty()) return snapshotInternal(topic)
        val stored = entryStore.store(persistable)
        if (!stored) return fallbackSnapshot(entries)
        topicStore.update(topic, persistable)
        return snapshotInternal(topic)
    }

    private fun fallbackSnapshot(entries: List<QueryResult.Entry>): TopicPostCacheSnapshot {
        if (entries.isEmpty()) return TopicPostCacheSnapshot(emptyList(), null)
        val deduped =
            entries
                .associateBy { it.id }
                .values
                .sortedByDescending { it.similarity }
        val lastFetchedAt = entries.maxOfOrNull { it.storedAt }
        return TopicPostCacheSnapshot(deduped, lastFetchedAt)
    }
}
