package straightway.mosa.content

import straightway.mosa.EmbeddingService
import straightway.mosa.VectorStore
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY

internal class ContentRestoreService(
    private val embeddingService: EmbeddingService,
    private val vectorStore: VectorStore,
) {
    suspend fun restore(posts: List<VectorStore.PostDump>) {
        val orderedPosts =
            posts
                .distinctBy(VectorStore.PostDump::id)
                .sortedWith(
                    compareBy<VectorStore.PostDump> { it.fields[ROOT_ID_KEY] != null }.thenBy(::storedAt).thenBy(VectorStore.PostDump::id),
                )

        vectorStore.writeAccess {
            orderedPosts.forEach { post ->
                add(
                    id = post.id,
                    text = post.text,
                    fields = post.fields,
                    vector = embeddingService.embed(post.text),
                )
            }
        }
    }

    private fun storedAt(post: VectorStore.PostDump): Long = post.fields[RECEIVED_AT_KEY]?.toLongOrNull() ?: Long.MAX_VALUE
}
