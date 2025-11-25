package straightway.mosa.client.ui

internal data class IdentityToolsCapabilities(
    val canDelete: Boolean,
    val canDownload: Boolean,
    val canCopy: Boolean,
    val canUpload: Boolean,
    val onDelete: () -> Unit,
    val onDownload: () -> Unit,
    val onCopy: () -> Unit,
    val onUpload: () -> Unit,
)

internal object IdentityToolsPresenter {
    fun present(capabilities: IdentityToolsCapabilities): List<IdentityToolAction> =
        listOf(
            IdentityToolAction(
                isEnabled = capabilities.canDelete,
                ariaKey = "button.identity.delete.aria",
                titleKey = "button.identity.delete.aria",
                iconClass = "fa fa-solid fa-trash-can",
                onTrigger = capabilities.onDelete,
            ),
            IdentityToolAction(
                isEnabled = capabilities.canCopy,
                ariaKey = "button.identity.copy.aria",
                titleKey = "button.identity.copy.label",
                iconClass = "fa fa-solid fa-copy",
                onTrigger = capabilities.onCopy,
            ),
            IdentityToolAction(
                isEnabled = capabilities.canDownload,
                ariaKey = "button.identity.download.aria",
                titleKey = "button.identity.download.label",
                iconClass = "fa fa-solid fa-download",
                onTrigger = capabilities.onDownload,
            ),
            IdentityToolAction(
                isEnabled = capabilities.canUpload,
                ariaKey = "button.identity.upload.aria",
                titleKey = "button.identity.upload.label",
                iconClass = "fa fa-solid fa-upload",
                onTrigger = capabilities.onUpload,
            ),
        )
}

internal data class IdentityToolAction(
    val isEnabled: Boolean,
    val ariaKey: String,
    val titleKey: String,
    val iconClass: String,
    val onTrigger: () -> Unit,
)
