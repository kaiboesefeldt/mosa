package straightway.mosa.client.content

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation
class IndexedDbPostCache(
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
        entries: List<TopicScopedPost>,
    ): PostCacheSnapshot =
        runCatching { appendInternal(topic, entries) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB topic cache append failed for $topic",
            ) {
                fallbackSnapshot(entries)
            }

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        runCatching { resolveInternal(ids) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB post resolution failed for ${ids.size} ids",
            ) {
                CachedPostResolution(emptyMap(), ids.toList())
            }

    override suspend fun evict(postId: String) {
        if (postId.isBlank()) return
        runCatching {
            topicStore.removePost(postId)
            entryStore.delete(listOf(postId))
        }.onFailureLogOrThrowCancellation(
            logger,
            "IndexedDB post eviction failed for $postId",
        )
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
        entries: List<TopicScopedPost>,
    ): PostCacheSnapshot {
        if (entries.isEmpty()) return snapshotInternal(topic)
        val persistable = entries.filter { it.post.id.isNotBlank() }
        if (persistable.isEmpty()) return snapshotInternal(topic)
        val stored = entryStore.store(persistable.map { it.post })
        if (!stored) return fallbackSnapshot(entries)
        topicStore.update(topic, persistable)
        return snapshotInternal(topic)
    }

    private suspend fun resolveInternal(ids: Collection<String>): CachedPostResolution {
        if (ids.isEmpty()) return CachedPostResolution(emptyMap(), emptyList())
        val resolution = entryStore.resolve(ids.distinct().map { TopicEntryRef(it, 0f) })
        return CachedPostResolution(
            postsById = resolution.resolved.associateBy { it.id },
            missingIds = resolution.missingIds,
        )
    }

    private fun fallbackSnapshot(entries: List<TopicScopedPost>): PostCacheSnapshot {
        if (entries.isEmpty()) return PostCacheSnapshot(emptyList(), null)
        val deduped =
            entries
                .associateBy { it.post.id }
                .values
                .sortedByDescending { it.similarity }
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt }
        return PostCacheSnapshot(deduped, lastFetchedAt)
    }
}
