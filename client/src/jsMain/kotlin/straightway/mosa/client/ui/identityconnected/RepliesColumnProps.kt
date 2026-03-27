package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.Ref
import web.html.HTMLDivElement

internal data class RepliesColumnProps(
    val visualsFactory: IdentityVisualsFactory,
    val content: ContentController,
    val moderation: ModerationController,
    val admin: AdminController,
    val selectedPost: straightway.mosa.content.QueryResult.Entry?,
    val connectedGid: String?,
    val canDeleteAsAdmin: Boolean,
    val isMobile: Boolean,
    val showBackToPosts: Boolean,
    val onBackToPosts: () -> Unit,
    val containerRef: Ref<HTMLDivElement>,
    val loadMoreRef: Ref<HTMLDivElement>,
)
