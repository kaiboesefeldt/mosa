package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.ClearTermsRenderer
import straightway.mosa.legal.clearterms.ClearTermsRenderedLanguage

fun interface PrivacyClearTermsContentProvider {
    suspend fun renderedLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): ClearTermsRenderedLanguage
}

class ClearTermsPrivacyContentProvider(
    private val clearTermsRenderer: ClearTermsRenderer,
) : PrivacyClearTermsContentProvider {
    override suspend fun renderedLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): ClearTermsRenderedLanguage = clearTermsRenderer.renderLanguage(baselineJson, profileJsons, languageTag)
}
