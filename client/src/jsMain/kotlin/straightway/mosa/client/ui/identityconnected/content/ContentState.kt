package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.ui.*

import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.content.QueryResult

internal fun extractTopicFilters(items: List<Topic>): Map<String, Float> =
    items.mapNotNull { topic ->
        val value = topic.scoreMustBeAbove
        if (value == null) {
            null
        } else {
            topic.title to value
        }
    }.toMap()

internal fun rootIdFor(entry: QueryResult.Entry): String = entry.rootId ?: entry.id

internal fun findTopicByTitle(
    topics: List<Topic>,
    title: String?,
): Topic? = title?.let { topicTitle -> topics.firstOrNull { it.title == topicTitle } }

internal fun findPostById(
    posts: List<TopicPosts>,
    id: String?,
): QueryResult.Entry? =
    id?.let { postId ->
        posts.firstNotNullOfOrNull { topicPosts ->
            topicPosts.entries.firstOrNull { it.id == postId }
        }
    }

internal fun removePostsFromTopicState(
    posts: List<TopicPosts>,
    postIds: Collection<String>,
): List<TopicPosts> {
    if (postIds.isEmpty()) return posts
    val idSet = postIds.toSet()
    return posts.mapNotNull { topicPosts ->
        val filtered = topicPosts.entries.filterNot { it.id in idSet }
        when {
            filtered.isEmpty() -> null
            filtered.size == topicPosts.entries.size -> topicPosts
            else -> topicPosts.copy(entries = filtered)
        }
    }
}

internal fun removeRepliesFromStateEntries(
    replies: ThreadReplies?,
    postIds: Collection<String>,
): ThreadReplies? {
    if (replies == null || postIds.isEmpty()) return replies
    val idSet = postIds.toSet()
    if (replies.entries.none { it.id in idSet }) return replies
    return replies.copy(entries = replies.entries.filterNot { it.id in idSet })
}

internal fun replaceTopicPosts(
    posts: List<TopicPosts>,
    updated: TopicPosts,
): List<TopicPosts> {
    val replaced =
        posts.map { topicPosts ->
            if (topicPosts.topic.title == updated.topic.title) {
                updated
            } else {
                topicPosts
            }
        }
    return if (replaced.any { it.topic.title == updated.topic.title }) {
        replaced
    } else {
        replaced + updated
    }
}
