package straightway.mosa.client.ui.identityconnected

import straightway.mosa.client.ui.IdentityVisualsFactory
import react.Ref
import straightway.mosa.client.i18n.Translator
import straightway.mosa.message.AdminMessage
import web.html.HTMLDivElement

internal data class AdminMessageHistorySectionProps(
    val translator: Translator,
    val identityVisualsFactory: IdentityVisualsFactory,
    val historyEntries: List<AdminMessage>,
    val isLoading: Boolean,
    val isAppending: Boolean,
    val errorText: String?,
    val hasMore: Boolean,
    val listRef: Ref<HTMLDivElement>,
    val onScrollToEnd: () -> Unit,
)

internal data class AdminMessageComposePopoverProps(
    val translator: Translator,
    val recipientName: String,
    val subject: String,
    val body: String,
    val isSending: Boolean,
    val errorText: String?,
    val onSubjectChange: (String) -> Unit,
    val onBodyChange: (String) -> Unit,
    val onClose: () -> Unit,
    val onSend: () -> Unit,
)
