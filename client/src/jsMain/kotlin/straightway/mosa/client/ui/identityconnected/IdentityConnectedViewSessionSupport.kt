package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import straightway.mosa.client.content.Topic
import straightway.mosa.content.QueryResult

internal fun setPaneForSelectedTopic(
    isMobile: Boolean,
    setMobilePane: (MobilePane) -> Unit,
    topic: Topic?,
) {
    if (!isMobile) return
    setMobilePane(if (topic == null) MobilePane.Topics else MobilePane.Posts)
}

internal fun setPaneForSelectedPost(
    isMobile: Boolean,
    setMobilePane: (MobilePane) -> Unit,
    entry: QueryResult.Entry?,
) {
    if (!isMobile) return
    setMobilePane(if (entry == null) MobilePane.Posts else MobilePane.Replies)
}

internal suspend fun persistSessionSnapshot(
    sessionStore: IdentitySessionStore,
    builder: (IdentitySessionSnapshot?) -> IdentitySessionSnapshot,
) {
    val currentSnapshot = runCatching { sessionStore.load() }.getOrNull()
    sessionStore.save(builder(currentSnapshot))
}

internal suspend fun persistProfileVisibilitySnapshot(
    sessionStore: IdentitySessionStore,
    visible: Boolean,
) {
    persistSessionSnapshot(sessionStore) { snapshot ->
        IdentitySessionSnapshot(
            connectedIdentityGid = snapshot?.connectedIdentityGid,
            isProfileVisible = visible,
            selectedTopicTitle = snapshot?.selectedTopicTitle,
        )
    }
}
