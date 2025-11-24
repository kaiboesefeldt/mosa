package straightway.mosa.client.topic

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
import straightway.mosa.content.QueryResult

interface PostCache {
    suspend fun snapshot(topic: String): PostCacheSnapshot
    suspend fun append(topic: String, entries: List<QueryResult.Entry>): PostCacheSnapshot
    suspend fun clear(topic: String)
}

internal class IndexedDbPostCache(
    private val entryStore: TopicEntryStore,
    private val topicStore: TopicReferenceStore,
    private val logger: Logger,
) : PostCache {
    override suspend fun snapshot(topic: String): PostCacheSnapshot =
        runCatching { snapshotInternal(topic) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache snapshot failed for $topic",
            ) {
                PostCacheSnapshot(emptyList(), null)
            }

    override suspend fun append(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): PostCacheSnapshot =
        runCatching { appendInternal(topic, entries) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache append failed for $topic",
            ) {
                fallbackSnapshot(entries)
            }

    override suspend fun clear(topic: String) {
        runCatching { topicStore.clear(topic) }
            .onFailureLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache clear failed for $topic"
            )
    }

    private suspend fun snapshotInternal(topic: String): PostCacheSnapshot {
        val record = topicStore.load(topic) ?: return PostCacheSnapshot(emptyList(), null)
        val resolution = entryStore.resolve(record.entries)
        if (resolution.missingIds.isNotEmpty()) {
            topicStore.removeMissing(topic, resolution.missingIds)
        }
        val postsById = resolution.resolved.associateBy { it.id }
        val sorted =
            record.entries
                .mapNotNull { ref ->
                    postsById[ref.id]?.let { post -> TopicScopedPost(post, ref.similarity) }
                }
                .sortedByDescending { it.similarity }
        return PostCacheSnapshot(sorted, record.lastFetchedAt)
    }

    private suspend fun appendInternal(
        topic: String,
        entries: List<QueryResult.Entry>,
    ): PostCacheSnapshot {
        if (entries.isEmpty()) return snapshotInternal(topic)
        val persistable = entries.filter { it.id.isNotBlank() }
        if (persistable.isEmpty()) return snapshotInternal(topic)
        val stored = entryStore.store(persistable.map { it.toCachedPost() })
        if (!stored) return fallbackSnapshot(entries)
        topicStore.update(topic, persistable)
        return snapshotInternal(topic)
    }

    private fun fallbackSnapshot(entries: List<QueryResult.Entry>): PostCacheSnapshot {
        if (entries.isEmpty()) return PostCacheSnapshot(emptyList(), null)
        val deduped =
            entries
                .associateBy { it.id }
                .values
                .sortedByDescending { it.similarity }
                .map { entry -> TopicScopedPost(entry.toCachedPost(), entry.similarity) }
        val lastFetchedAt = entries.maxOfOrNull { it.storedAt }
        return PostCacheSnapshot(deduped, lastFetchedAt)
    }

    private fun QueryResult.Entry.toCachedPost(): CachedPost =
        CachedPost(
            id = id,
            content = content,
            authorGID = authorGID,
            storedAt = storedAt,
            rootId = rootId,
        )
}