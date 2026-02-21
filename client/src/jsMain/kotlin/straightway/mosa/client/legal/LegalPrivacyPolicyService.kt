package straightway.mosa.client.legal

import straightway.mosa.client.content.PodAccessFactory
import straightway.mosa.client.pod.PodBaseProvider

class LegalPrivacyPolicyService(
    private val podBaseProvider: PodBaseProvider,
    private val podAccessFactory: PodAccessFactory,
    private val preferredLanguageTagsProvider: () -> List<String>,
    private val clearTermsContentProvider: PrivacyClearTermsContentProvider,
    private val markdownToHtmlRenderer: MarkdownToHtmlRenderer = MarkdownToHtmlRenderer(),
) {
    suspend fun loadPrivacyPolicyHtml(): String {
        val podBase = podBaseProvider.base() ?: throw IllegalStateException("No pod base available")
        val podAccess = podAccessFactory(podBase)
        val baseline = podAccess.privacyBaselineClearTerms()
        val profiles =
            listOf(
                podAccess.privacyDefaultClearTerms(),
                podAccess.privacyMosaClearTerms(),
                podAccess.privacyOperatorClearTerms(),
            ).distinct()
        val textsByLanguage = clearTermsContentProvider.renderedTextsByLanguage(baseline, profiles)
        val markdown = selectMarkdownByLanguage(textsByLanguage, preferredLanguageTagsProvider())
        return markdownToHtmlRenderer.render(markdown)
    }

    private fun selectMarkdownByLanguage(
        textsByLanguage: Map<String, String>,
        preferredLanguageTags: List<String>,
    ): String {
        if (textsByLanguage.isEmpty()) {
            throw IllegalStateException("No ClearTerms text available")
        }

        val normalizedTexts = textsByLanguage.entries.associateBy({ normalizeLanguageTag(it.key) }, { it.value })
        val candidates =
            buildList {
                preferredLanguageTags.forEach { tag ->
                    val normalized = normalizeLanguageTag(tag)
                    if (normalized.isNotBlank()) {
                        add(normalized)
                        val primary = normalized.substringBefore('-')
                        if (primary.isNotBlank()) {
                            add(primary)
                        }
                    }
                }
                add("en")
            }.distinct()

        candidates.forEach { candidate ->
            normalizedTexts[candidate]?.let { return it }
        }

        normalizedTexts["en"]?.let { return it }

        return textsByLanguage.entries.minBy { it.key.lowercase() }.value
    }

    private fun normalizeLanguageTag(languageTag: String): String = languageTag.trim().lowercase()
}
