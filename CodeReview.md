## Findings

1. `server/src/main/kotlin/straightway/mosa/content/ContentDeleteService.kt:104-113` - High - rollback does not restore the original indexed state. `restorePosts()` re-embeds `post.text` and writes a fresh vector instead of restoring the vector that was actually deleted, but `VectorStore.PostDump` does not carry the original vector. That means a failed delete can silently come back with different search results, and rollback can now fail for reasons unrelated to the original deletion error if the embedder is unavailable or nondeterministic. The smallest safe fix is to preserve and restore the original vector payload as part of the rollback path, or avoid deleting until a restoration path with the original vector is available.

## Residual Risk

- The new paging and descendant-traversal coverage looks directionally correct, but the rollback path still needs evidence that it restores the exact pre-delete vector-store state under failure.
