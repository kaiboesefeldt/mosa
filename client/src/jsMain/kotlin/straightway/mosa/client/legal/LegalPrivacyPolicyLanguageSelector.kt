package straightway.mosa.client.legal

import straightway.mosa.legal.clearterms.parse.JsonParser
import straightway.mosa.legal.clearterms.parse.DocumentReader
import straightway.mosa.legal.clearterms.placeholder.PlaceholderParser
import straightway.mosa.legal.clearterms.validation.ProfileValidator

internal class LegalPrivacyPolicyLanguageSelector(
    private val clearTermsJsonParser: JsonParser = JsonParser(),
    private val clearTermsDocumentReader: DocumentReader = DocumentReader(),
    private val clearTermsProfileValidator: ProfileValidator = ProfileValidator(PlaceholderParser()),
) {
    fun select(
        baselineJson: String,
        profileJsons: List<String>,
        preferredLanguageTags: List<String>,
    ): String {
        val languageTags = availableLanguageTags(baselineJson, profileJsons)
        check(languageTags.isNotEmpty()) { "No ClearTerms text available" }
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
            if (candidate in languageTags) {
                return candidate
            }
        }

        if ("en" in languageTags) {
            return "en"
        }

        return languageTags.minBy { it.lowercase() }
    }

    private fun availableLanguageTags(
        baselineJson: String,
        profileJsons: List<String>,
    ): Set<String> {
        val baseline = clearTermsDocumentReader.readBaseline(clearTermsJsonParser.parse(baselineJson))
        clearTermsProfileValidator.validateBaselineStructure(baseline)
        var textsByLanguage =
            baseline.textsByLanguage.entries.associate { (language, blocks) ->
                normalizeLanguageTag(language) to blocks
            }
        profileJsons.forEach { profileJson ->
            val profile = clearTermsDocumentReader.readProfile(clearTermsJsonParser.parse(profileJson))
            textsByLanguage = clearTermsProfileValidator.applyTextOverrides(textsByLanguage, profile.overrides.texts)
        }
        clearTermsProfileValidator.validateFinalTextsResolved(textsByLanguage)
        return textsByLanguage.keys
    }

    private fun normalizeLanguageTag(languageTag: String): String = languageTag.trim().lowercase()
}
