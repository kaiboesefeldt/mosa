package straightway.mosa.client.content

import straightway.mosa.content.QueryResult

data class TopicPosts(
    val topic: Topic,
    val entries: List<QueryResult.Entry>,
)
