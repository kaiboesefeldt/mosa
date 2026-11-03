package straightway.mosa.client.legal

data class LegalComparisonSelection(
    val groupId: String,
    val blockId: String,
)

sealed interface LegalDialogState {
    data object Idle : LegalDialogState

    data object Loading : LegalDialogState

    data class Ready(
        val document: LegalPrivacyPolicyDocument,
    ) : LegalDialogState

    data class Error(
        val text: String,
    ) : LegalDialogState
}

internal const val LEGAL_DOCUMENT_PRIVACY = "privacy"
internal const val LEGAL_DOCUMENT_IMPRINT = "imprint"
internal const val LEGAL_DOCUMENT_TERMS = "terms"
internal const val LEGAL_DOCUMENT_LICENSES = "licenses"
