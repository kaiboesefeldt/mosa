package straightway.mosa.client.content

import straightway.mosa.client.logging.Logger
import straightway.mosa.client.util.getOrElseLogOrThrowCancellation
import straightway.mosa.client.util.onFailureLogOrThrowCancellation

class IndexedDbReplyCache(
    private val entryStore: TopicEntryStore,
    private val referenceStore: ReplyReferenceStore,
    private val logger: Logger,
) : ReplyCache {
    override suspend fun snapshot(rootId: String): ReplyCacheSnapshot =
        runCatching { snapshotInternal(rootId) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB reply cache snapshot failed for $rootId",
            ) {
                ReplyCacheSnapshot(emptyList(), null)
            }

    override suspend fun append(
        rootId: String,
        entries: List<ThreadScopedReply>,
    ): ReplyCacheSnapshot =
        runCatching { appendInternal(rootId, entries) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB reply cache append failed for $rootId",
            ) {
                fallbackSnapshot(entries)
            }

    override suspend fun resolve(ids: Collection<String>): CachedPostResolution =
        runCatching { resolveInternal(ids) }
            .getOrElseLogOrThrowCancellation(
                logger,
                "IndexedDB reply resolution failed for ${ids.size} ids",
            ) {
                CachedPostResolution(emptyMap(), ids.toList())
            }

    override suspend fun evict(postId: String) {
        if (postId.isBlank()) return
        runCatching {
            referenceStore.removePost(postId)
            entryStore.delete(listOf(postId))
        }.onFailureLogOrThrowCancellation(
            logger,
            "IndexedDB reply eviction failed for $postId",
        )
    }

    override suspend fun clear(rootId: String) {
        runCatching { referenceStore.clear(rootId) }
            .onFailureLogOrThrowCancellation(
                logger,
                "IndexedDB reply cache clear failed for $rootId",
            )
    }

    override suspend fun clearAll() {
        runCatching { referenceStore.clearAll() }
            .onFailureLogOrThrowCancellation(
                logger,
                "IndexedDB reply cache clear-all failed",
            )
    }

    private suspend fun snapshotInternal(rootId: String): ReplyCacheSnapshot {
        val record = referenceStore.load(rootId) ?: return ReplyCacheSnapshot(emptyList(), null)
        val resolution = entryStore.resolve(record.entries.map { TopicEntryRef(it.id, 0f) })
        if (resolution.missingIds.isNotEmpty()) {
            referenceStore.removeMissing(rootId, resolution.missingIds)
        }
        val postsById = resolution.resolved.associateBy { it.id }
        val sorted =
            record.entries
                .mapNotNull { ref ->
                    postsById[ref.id]?.let { post -> ThreadScopedReply(post) }
                }
                .sortedBy { it.post.storedAt }
        return ReplyCacheSnapshot(sorted, record.lastFetchedAt)
    }

    private suspend fun appendInternal(
        rootId: String,
        entries: List<ThreadScopedReply>,
    ): ReplyCacheSnapshot {
        if (entries.isEmpty()) return snapshotInternal(rootId)
        val persistable = entries.filter { it.post.id.isNotBlank() }
        if (persistable.isEmpty()) return snapshotInternal(rootId)
        val stored = entryStore.store(persistable.map { it.post })
        if (!stored) return fallbackSnapshot(entries)
        referenceStore.update(rootId, persistable)
        return snapshotInternal(rootId)
    }

    private suspend fun resolveInternal(ids: Collection<String>): CachedPostResolution {
        if (ids.isEmpty()) return CachedPostResolution(emptyMap(), emptyList())
        val resolution = entryStore.resolve(ids.distinct().map { TopicEntryRef(it, 0f) })
        return CachedPostResolution(
            postsById = resolution.resolved.associateBy { it.id },
            missingIds = resolution.missingIds,
        )
    }

    private fun fallbackSnapshot(entries: List<ThreadScopedReply>): ReplyCacheSnapshot {
        if (entries.isEmpty()) return ReplyCacheSnapshot(emptyList(), null)
        val deduped =
            entries
                .associateBy { it.post.id }
                .values
                .sortedBy { it.post.storedAt }
        val lastFetchedAt = entries.maxOfOrNull { it.post.storedAt }
        return ReplyCacheSnapshot(deduped, lastFetchedAt)
    }
}
