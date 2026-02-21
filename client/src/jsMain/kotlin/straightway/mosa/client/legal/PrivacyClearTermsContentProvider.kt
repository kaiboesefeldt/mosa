package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.ClearTermsRenderer

interface PrivacyClearTermsContentProvider {
    suspend fun renderedTextsByLanguage(
        baselineJson: String,
        profileJsons: List<String>,
    ): Map<String, String>
}

class ClearTermsPrivacyContentProvider(
    private val clearTermsRenderer: ClearTermsRenderer,
) : PrivacyClearTermsContentProvider {
    override suspend fun renderedTextsByLanguage(
        baselineJson: String,
        profileJsons: List<String>,
    ): Map<String, String> =
        clearTermsRenderer.renderAll(baselineJson, profileJsons).textsByLanguage
}
