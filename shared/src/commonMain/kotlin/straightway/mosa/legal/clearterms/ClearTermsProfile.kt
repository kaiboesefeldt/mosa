package straightway.mosa.legal.clearterms

data class ClearTermsProfile(
    val id: String,
    val version: String,
    val parent: ClearTermsReference,
    val overrides: Overrides,
) {
    data class Overrides(
        val values: Map<String, ClearTermsJsonNode.ScalarNode>,
        val texts: Map<String, TextOverrides>?,
    )

    data class TextOverrides(
        val replace: Map<String, String>,
        val append: List<ClearTermsTextBlock>,
    )
}
