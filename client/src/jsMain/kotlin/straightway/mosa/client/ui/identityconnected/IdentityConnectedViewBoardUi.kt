package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import straightway.mosa.client.i18n.Translator
import web.cssom.ClassName

internal fun ChildrenBuilder.connectedContentBoard(
    translator: Translator,
    props: IdentityConnectedViewBoardProps,
) = div {
    className = ClassName(props.shell.boardClass)
    div {
        className = ClassName("topic-board__track")
        if (props.viewport.isMobile) {
            asDynamic().style = mobileTrackStyle(props.shell.combinedShiftPercent)
            onTouchStart = props.shell.onTouchStart
            onTouchMove = props.shell.onTouchMove
            onTouchEnd = props.shell.onTouchEnd
            onTouchCancel = props.shell.onTouchEnd
        }
        topicColumn(
            translator,
            props.content.topics,
            props.content.selectedTopic,
            props.content.addTopic,
            props.content.selectTopic,
            props.content.removeTopic,
        )
        postsColumn(
            translator,
            IdentityConnectedViewPostsColumnProps(
                visualsFactory = props.visualsFactory,
                content = props.content,
                moderation = props.moderation,
                admin = props.admin,
                selectedTopic = props.content.selectedTopic,
                selectedPost = props.content.selectedPost,
                connectedGid = props.profile.connectedIdentityGid,
                canDeleteAsAdmin = props.adminPresentation.canDeleteAsAdmin,
                showBackToTopics = props.shell.showBackButtons && props.viewport.mobilePane != MobilePane.Topics,
                onBackToTopics = { props.viewport.setMobilePane(MobilePane.Topics) },
                containerRef = props.postsContainerRef,
                loadMoreRef = props.loadMoreRef,
            ),
        )
        repliesColumn(
            translator,
            IdentityConnectedViewRepliesColumnProps(
                visualsFactory = props.visualsFactory,
                content = props.content,
                moderation = props.moderation,
                admin = props.admin,
                selectedPost = props.content.selectedPost,
                connectedGid = props.profile.connectedIdentityGid,
                canDeleteAsAdmin = props.adminPresentation.canDeleteAsAdmin,
                isMobile = props.viewport.isMobile,
                showBackToPosts = props.shell.showBackButtons,
                onBackToPosts = {
                    if (props.content.selectedTopic == null) {
                        props.viewport.setMobilePane(MobilePane.Topics)
                    } else {
                        props.viewport.setMobilePane(MobilePane.Posts)
                    }
                },
                containerRef = props.repliesContainerRef,
                loadMoreRef = props.repliesLoadMoreRef,
            ),
        )
    }
    if (props.uiState.shouldRenderFloatingComposer) {
        floatingComposerButton(
            label = translator.translate("connected.composer.open"),
            onActivate = props.content.openComposer,
        )
    }
}

private fun mobileTrackStyle(combinedShiftPercent: Double): dynamic {
    val style = js("({})")
    style["--mobile-shift"] = "${combinedShiftPercent}%"
    return style
}
