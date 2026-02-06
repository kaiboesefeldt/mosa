package straightway.mosa.legal.clearterms

data class ClearTermsDeviationProfile(
    val id: String,
    val version: String,
    val default: ClearTermsReference,
    val overrides: Overrides,
) {
    data class Overrides(
        val values: Map<String, ClearTermsYamlNode.ScalarNode>,
        val texts: Map<String, TextOverrides>?,
    )

    data class TextOverrides(
        val replace: Map<String, String>,
        val append: List<ClearTermsTextBlock>,
    )
}
