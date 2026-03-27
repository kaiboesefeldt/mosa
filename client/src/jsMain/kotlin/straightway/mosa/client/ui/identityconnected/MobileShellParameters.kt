package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.content.QueryResult

internal data class MobileShellState(
    val isMobile: Boolean,
    val isTouchDevice: Boolean,
    val mobilePane: MobilePane,
    val selectedTopic: Topic?,
    val selectedPost: QueryResult.Entry?,
    val posts: List<TopicPosts>,
    val replies: ThreadReplies?,
)

internal data class MobileShellRefs(
    val contentRefs: ConnectedViewContentRefs,
)

internal data class MobileShellActions(
    val setMobilePane: (MobilePane) -> Unit,
    val findTopicByTitle: (String?) -> Topic?,
    val findPostById: (String?) -> QueryResult.Entry?,
    val selectTopic: (Topic?) -> Unit,
    val selectPost: (QueryResult.Entry) -> Unit,
    val loadMorePosts: () -> Unit,
    val loadMoreReplies: () -> Unit,
)
