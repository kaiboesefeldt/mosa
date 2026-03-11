package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

data class ThreadScopedReply(
    val post: CachedPost,
) {
    fun asQueryResultEntry(): QueryResult.Entry =
        QueryResult.Entry(
            id = post.id,
            content = post.content,
            similarity = 0f,
            authorGID = post.authorGID,
            storedAt = post.storedAt,
            rootId = post.rootId,
            authorPublicKey = post.authorPublicKey,
            authorSignature = post.authorSignature,
            signatureVersion = post.signatureVersion,
        )
}
