package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.ClearTermsRenderer
import straightway.mosa.legal.clearterms.ClearTermsRenderResult

fun interface PrivacyClearTermsContentProvider {
    suspend fun renderedTextsByLanguage(
        baselineJson: String,
        profileJsons: List<String>,
    ): ClearTermsRenderResult
}

class ClearTermsPrivacyContentProvider(
    private val clearTermsRenderer: ClearTermsRenderer,
) : PrivacyClearTermsContentProvider {
    override suspend fun renderedTextsByLanguage(
        baselineJson: String,
        profileJsons: List<String>,
    ): ClearTermsRenderResult = clearTermsRenderer.renderAll(baselineJson, profileJsons)
}
