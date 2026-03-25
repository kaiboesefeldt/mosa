package straightway.mosa.client.ui

import react.Ref
import web.html.HTMLDivElement

internal data class IdentityConnectedViewRepliesColumnProps(
    val visualsFactory: IdentityVisualsFactory,
    val content: IdentityConnectedViewContentController,
    val moderation: IdentityConnectedViewModerationController,
    val admin: IdentityConnectedViewAdminController,
    val selectedPost: straightway.mosa.content.QueryResult.Entry?,
    val connectedGid: String?,
    val canDeleteAsAdmin: Boolean,
    val isMobile: Boolean,
    val showBackToPosts: Boolean,
    val onBackToPosts: () -> Unit,
    val containerRef: Ref<HTMLDivElement>,
    val loadMoreRef: Ref<HTMLDivElement>,
)
