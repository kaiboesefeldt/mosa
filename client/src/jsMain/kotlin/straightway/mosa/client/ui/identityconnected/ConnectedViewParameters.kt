package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.*

import kotlinx.coroutines.CoroutineScope
import react.RefObject
import straightway.mosa.client.i18n.Translator
import web.html.HTMLDivElement

internal data class ConnectedViewAsyncContext(
    val translator: Translator,
    val scope: CoroutineScope,
)

internal data class ConnectedViewFeedbackContext(
    val async: ConnectedViewAsyncContext,
    val showToast: (String) -> Unit,
)

internal data class ConnectedViewContentRefs(
    val postsContainerRef: RefObject<HTMLDivElement>,
    val loadMoreRef: RefObject<HTMLDivElement>,
    val repliesContainerRef: RefObject<HTMLDivElement>,
    val repliesLoadMoreRef: RefObject<HTMLDivElement>,
)
