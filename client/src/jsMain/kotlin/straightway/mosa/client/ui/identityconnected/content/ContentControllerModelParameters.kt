package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.content.QueryResult

internal data class ContentControllerState(
    val topics: List<Topic>,
    val selectedTopic: Topic?,
    val posts: List<TopicPosts>,
    val selectedPost: QueryResult.Entry?,
    val replies: ThreadReplies?,
    val isLoadingReplies: Boolean,
    val topicFilters: Map<String, Float>,
    val isLoadingPosts: Boolean,
    val isComposerOpen: Boolean,
    val composerText: String,
    val isPublishing: Boolean,
    val publishError: String?,
    val deleteCandidate: QueryResult.Entry?,
    val isDeletingPost: Boolean,
    val isDeletingAllPosts: Boolean,
    val deleteError: String?,
    val isReplyComposerOpen: Boolean,
    val replyComposerText: String,
    val isReplyPublishing: Boolean,
    val replyPublishError: String?,
)

internal data class ContentControllerActions(
    val setComposerText: (String) -> Unit,
    val setReplyComposerText: (String) -> Unit,
    val addTopic: () -> Unit,
    val removeTopic: (Topic) -> Unit,
    val selectTopic: (Topic?) -> Unit,
    val openComposer: () -> Unit,
    val closeComposer: () -> Unit,
    val sendPost: () -> Unit,
    val requestDelete: (QueryResult.Entry) -> Unit,
    val clearDeleteCandidate: () -> Unit,
    val confirmDelete: (QueryResult.Entry) -> Unit,
    val deleteAllPosts: () -> Unit,
    val openReplyComposer: () -> Unit,
    val closeReplyComposer: () -> Unit,
    val sendReply: () -> Unit,
    val clearTopicFilter: (Topic) -> Unit,
    val toggleTopicFilter: (Topic, QueryResult.Entry) -> Unit,
    val loadMorePosts: () -> Unit,
    val loadMoreReplies: () -> Unit,
    val selectPost: (QueryResult.Entry) -> Unit,
    val clearReplies: () -> Unit,
)

internal data class ContentControllerLookups(
    val findTopicByTitle: (String?) -> Topic?,
    val findPostById: (String?) -> QueryResult.Entry?,
)
