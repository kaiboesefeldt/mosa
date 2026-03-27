package straightway.mosa.client.ui.identityconnected.content

import straightway.mosa.client.ui.identityconnected.admin.*
import straightway.mosa.client.ui.identityconnected.common.*
import straightway.mosa.client.ui.identityconnected.moderation.*

import straightway.mosa.client.i18n.Translator

internal data class ComplaintComposerPopoverProps(
    val translator: Translator,
    val title: String,
    val placeholder: String,
    val text: String,
    val isSubmitting: Boolean,
    val errorText: String?,
    val sendLabel: String,
    val onTextChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onClose: () -> Unit,
)
