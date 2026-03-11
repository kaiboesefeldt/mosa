package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

data class TopicScopedPost(
    val post: CachedPost,
    val similarity: Float,
) {
    fun asQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = post.id,
            content = post.content,
            similarity = similarity,
            authorGID = post.authorGID,
            storedAt = post.storedAt,
            rootId = post.rootId,
            authorPublicKey = post.authorPublicKey,
            authorSignature = post.authorSignature,
            signatureVersion = post.signatureVersion,
        )
}
