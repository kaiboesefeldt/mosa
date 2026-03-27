package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.ClearTermsRenderer
import straightway.mosa.legal.clearterms.render.RenderedLanguage

fun interface PrivacyClearTermsContentProvider {
    suspend fun renderedLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): RenderedLanguage
}

class ClearTermsPrivacyContentProvider(
    private val clearTermsRenderer: ClearTermsRenderer,
) : PrivacyClearTermsContentProvider {
    override suspend fun renderedLanguage(
        baselineJson: String,
        profileJsons: List<String>,
        languageTag: String,
    ): RenderedLanguage = clearTermsRenderer.renderLanguage(baselineJson, profileJsons, languageTag)
}
