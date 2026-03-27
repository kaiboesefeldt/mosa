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
    className = ClassName(props.shell.viewState.boardClass)
    div {
        className = ClassName("topic-board__track")
        if (props.viewport.state.isMobile) {
            asDynamic().style = mobileTrackStyle(props.shell.viewState.combinedShiftPercent)
            onTouchStart = props.shell.touchHandlers.onTouchStart
            onTouchMove = props.shell.touchHandlers.onTouchMove
            onTouchEnd = props.shell.touchHandlers.onTouchEnd
            onTouchCancel = props.shell.touchHandlers.onTouchEnd
        }
        topicColumn(
            translator,
            props.content.state.topics,
            props.content.state.selectedTopic,
            props.content.actions.addTopic,
            props.content.actions.selectTopic,
            props.content.actions.removeTopic,
        )
        postsColumn(
            translator,
            IdentityConnectedViewPostsColumnProps(
                visualsFactory = props.visualsFactory,
                content = props.content,
                moderation = props.moderation,
                admin = props.admin,
                selectedTopic = props.content.state.selectedTopic,
                selectedPost = props.content.state.selectedPost,
                connectedGid = props.profile.connectedIdentityGid,
                canDeleteAsAdmin = props.adminPresentation.canDeleteAsAdmin,
                showBackToTopics = props.shell.viewState.showBackButtons && props.viewport.state.mobilePane != MobilePane.Topics,
                onBackToTopics = { props.viewport.actions.setMobilePane(MobilePane.Topics) },
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
                selectedPost = props.content.state.selectedPost,
                connectedGid = props.profile.connectedIdentityGid,
                canDeleteAsAdmin = props.adminPresentation.canDeleteAsAdmin,
                isMobile = props.viewport.state.isMobile,
                showBackToPosts = props.shell.viewState.showBackButtons,
                onBackToPosts = {
                    if (props.content.state.selectedTopic == null) {
                        props.viewport.actions.setMobilePane(MobilePane.Topics)
                    } else {
                        props.viewport.actions.setMobilePane(MobilePane.Posts)
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
            onActivate = props.content.actions.openComposer,
        )
    }
}

private fun mobileTrackStyle(combinedShiftPercent: Double): dynamic {
    val style = js("({})")
    style["--mobile-shift"] = "${combinedShiftPercent}%"
    return style
}
