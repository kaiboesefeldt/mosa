@file:OptIn(ExperimentalWasmJsInterop::class)

package straightway.mosa.client.ui

import kotlinx.browser.window
import kotlinx.coroutines.awaitCancellation
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsName
import kotlin.js.unsafeCast
import kotlin.math.abs
import react.RefObject
import react.dom.events.TouchEvent
import react.useEffect
import react.useRef
import react.useState
import straightway.mosa.client.content.ThreadReplies
import straightway.mosa.client.content.Topic
import straightway.mosa.client.content.TopicPosts
import straightway.mosa.content.QueryResult
import web.dom.Element
import web.html.HTMLDivElement

private external interface JsIntersectionObserverEntry {
    val isIntersecting: Boolean
}

private external interface JsIntersectionObserverOptions {
    var root: Element?
    var rootMargin: String?
    var threshold: dynamic
}

@JsName("IntersectionObserver")
private external class JsIntersectionObserver(
    callback: (Array<JsIntersectionObserverEntry>, JsIntersectionObserver) -> Unit,
    options: JsIntersectionObserverOptions = definedExternally,
) {
    fun observe(target: Element)
    fun disconnect()
}

private data class SwipeTargets(
    var topic: Topic?,
    var post: QueryResult.Entry?,
)

internal data class IdentityConnectedViewMobileShell(
    val swipeOffsetPercent: Double,
    val showBackButtons: Boolean,
    val boardClass: String,
    val combinedShiftPercent: Double,
    val onTouchStart: ((TouchEvent<*>) -> Unit)?,
    val onTouchMove: ((TouchEvent<*>) -> Unit)?,
    val onTouchEnd: ((TouchEvent<*>) -> Unit)?,
)

internal fun useIdentityConnectedViewMobileShell(
    isMobile: Boolean,
    isTouchDevice: Boolean,
    mobilePane: MobilePane,
    setMobilePane: (MobilePane) -> Unit,
    selectedTopic: Topic?,
    selectedPost: QueryResult.Entry?,
    posts: List<TopicPosts>,
    replies: ThreadReplies?,
    postsContainerRef: RefObject<HTMLDivElement>,
    loadMoreRef: RefObject<HTMLDivElement>,
    repliesContainerRef: RefObject<HTMLDivElement>,
    repliesLoadMoreRef: RefObject<HTMLDivElement>,
    findTopicByTitle: (String?) -> Topic?,
    findPostById: (String?) -> QueryResult.Entry?,
    selectTopic: (Topic?) -> Unit,
    selectPost: (QueryResult.Entry) -> Unit,
    loadMorePosts: () -> Unit,
    loadMoreReplies: () -> Unit,
): IdentityConnectedViewMobileShell {
    val touchStartXRef = useRef(0.0)
    val touchStartYRef = useRef(0.0)
    val swipePaneRef = useRef(MobilePane.Topics)
    val swipeActiveRef = useRef(false)
    val swipeTargetsRef = useRef(SwipeTargets(null, null))
    var swipeOffsetPercent by useState(0.0)

    useEffect(arrayOf(posts, selectedTopic, isMobile, mobilePane)) {
        val target = loadMoreRef.current ?: return@useEffect
        val container = postsContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        if (isMobile && mobilePane != MobilePane.Posts) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        if (isMobile) {
            options.root = null
            options.rootMargin = "120px"
        } else {
            options.root = container
            options.rootMargin = "48px"
        }
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) loadMorePosts()
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    useEffect(arrayOf(replies, selectedPost, isMobile, mobilePane)) {
        val target = repliesLoadMoreRef.current ?: return@useEffect
        val container = repliesContainerRef.current ?: return@useEffect
        val observerAvailable = js("typeof IntersectionObserver !== 'undefined'") as Boolean
        if (!observerAvailable) return@useEffect
        if (isMobile && mobilePane != MobilePane.Replies) return@useEffect
        val options = js("{}").unsafeCast<JsIntersectionObserverOptions>()
        if (isMobile) {
            options.root = null
            options.rootMargin = "120px"
        } else {
            options.root = container
            options.rootMargin = "48px"
        }
        options.threshold = 1.0
        val observer =
            JsIntersectionObserver(
                { entries, _ ->
                    val shouldLoadMore = entries.any { entry -> entry.isIntersecting }
                    val hasScrollIntent = container.dataset["mosaScrollReady"] == "true"
                    if (shouldLoadMore && hasScrollIntent) loadMoreReplies()
                },
                options,
            )
        observer.observe(target)
        try {
            awaitCancellation()
        } finally {
            observer.disconnect()
        }
    }

    useEffect(arrayOf(isMobile, selectedTopic, selectedPost, mobilePane)) {
        if (!isMobile) return@useEffect
        if (selectedTopic == null && mobilePane != MobilePane.Topics) {
            setMobilePane(MobilePane.Topics)
            return@useEffect
        }
        if (selectedPost == null && mobilePane == MobilePane.Replies) {
            setMobilePane(MobilePane.Posts)
        }
    }

    val onTouchStart =
        if (!isMobile) {
            null
        } else {
            handler@{ event: TouchEvent<*> ->
                val touch = event.changedTouches.item(0) ?: return@handler
                touchStartXRef.current = touch.clientX
                touchStartYRef.current = touch.clientY
                swipePaneRef.current = mobilePane
                val targets = swipeTargetsRef.current ?: return@handler
                val eventTarget = event.target as? Element
                targets.topic = findTopicByTitle(closestAttribute(eventTarget, "data-topic-title"))
                targets.post = findPostById(closestAttribute(eventTarget, "data-post-id"))
                swipeActiveRef.current = true
                swipeOffsetPercent = 0.0
            }
        }
    val onTouchMove =
        if (!isMobile) {
            null
        } else {
            handler@{ event: TouchEvent<*> ->
                if (swipeActiveRef.current != true) return@handler
                val targets = swipeTargetsRef.current ?: return@handler
                val touch = event.changedTouches.item(0) ?: return@handler
                val startX = touchStartXRef.current ?: return@handler
                val startY = touchStartYRef.current ?: return@handler
                val deltaX = touch.clientX - startX
                val deltaY = abs(touch.clientY - startY)
                if (abs(deltaX) <= deltaY) {
                    swipeOffsetPercent = 0.0
                    return@handler
                }
                val activePane = swipePaneRef.current ?: mobilePane
                val trackWidth =
                    (event.currentTarget as? HTMLDivElement)?.clientWidth?.toDouble()?.coerceAtLeast(1.0)
                        ?: window.innerWidth.toDouble().coerceAtLeast(1.0)
                val percent = (deltaX / trackWidth) * 100.0
                when {
                    0.0 < deltaX -> {
                        if (activePane == MobilePane.Topics) {
                            swipeOffsetPercent = 0.0
                            return@handler
                        }
                        swipeOffsetPercent = percent.coerceIn(0.0, 100.0)
                    }
                    deltaX < 0.0 -> when (activePane) {
                        MobilePane.Topics -> {
                            swipeOffsetPercent = if (targets.topic == null) 0.0 else percent.coerceIn(-100.0, 0.0)
                        }
                        MobilePane.Posts -> {
                            swipeOffsetPercent = if (targets.post == null) 0.0 else percent.coerceIn(-100.0, 0.0)
                        }
                        MobilePane.Replies -> swipeOffsetPercent = 0.0
                    }
                }
            }
        }
    val onTouchEnd =
        if (!isMobile) {
            null
        } else {
            handler@{ _: TouchEvent<*> ->
                if (swipeActiveRef.current != true) return@handler
                val targets = swipeTargetsRef.current ?: return@handler
                val activePane = swipePaneRef.current ?: mobilePane
                if (18.0 < swipeOffsetPercent) {
                    when (activePane) {
                        MobilePane.Replies -> setMobilePane(MobilePane.Posts)
                        MobilePane.Posts -> setMobilePane(MobilePane.Topics)
                        MobilePane.Topics -> Unit
                    }
                }
                if (swipeOffsetPercent < -18.0) {
                    when (activePane) {
                        MobilePane.Topics -> targets.topic?.let { topic ->
                            selectTopic(topic)
                            setMobilePane(MobilePane.Posts)
                        }
                        MobilePane.Posts -> targets.post?.let(selectPost)
                        MobilePane.Replies -> Unit
                    }
                }
                swipeOffsetPercent = 0.0
                swipeActiveRef.current = false
                targets.topic = null
                targets.post = null
            }
        }

    val boardClass =
        buildString {
            append("topic-board")
            if (isMobile) {
                append(" topic-board--mobile")
                append(
                    when (mobilePane) {
                        MobilePane.Topics -> " topic-board--mobile-topics"
                        MobilePane.Posts -> " topic-board--mobile-posts"
                        MobilePane.Replies -> " topic-board--mobile-replies"
                    },
                )
                if (swipeOffsetPercent != 0.0) append(" topic-board--mobile-dragging")
            }
        }

    val combinedShiftPercent =
        when (mobilePane) {
            MobilePane.Topics -> 0.0
            MobilePane.Posts -> -100.0
            MobilePane.Replies -> -200.0
        } + if (isMobile) swipeOffsetPercent else 0.0

    return IdentityConnectedViewMobileShell(
        swipeOffsetPercent = swipeOffsetPercent,
        showBackButtons = isMobile && !isTouchDevice,
        boardClass = boardClass,
        combinedShiftPercent = combinedShiftPercent,
        onTouchStart = onTouchStart,
        onTouchMove = onTouchMove,
        onTouchEnd = onTouchEnd,
    )
}
