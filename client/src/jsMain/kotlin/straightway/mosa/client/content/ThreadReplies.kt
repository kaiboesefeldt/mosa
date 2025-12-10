package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

data class ThreadReplies(
    val rootId: String,
    val entries: List<QueryResult.Entry>,
)
