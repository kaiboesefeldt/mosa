package straightway.mosa.legal.clearterms.document

import straightway.mosa.legal.clearterms.parse.JsonNode

data class Profile(
    val id: String,
    val version: String,
    val parent: Reference,
    val overrides: Overrides,
) {
    data class Overrides(
        val values: Map<String, JsonNode.ScalarNode>?,
        val texts: Map<String, TextOverrides>?,
    )

    data class TextOverrides(
        val replace: Map<String, String>,
        val append: List<TextBlock>?,
    )
}
