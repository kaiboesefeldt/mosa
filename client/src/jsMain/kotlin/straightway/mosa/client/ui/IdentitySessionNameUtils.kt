package straightway.mosa.client.ui

import straightway.mosa.identity.FictionalName

fun FictionalName.toIdentityDownloadFileName(): String {
    val tokens =
        buildList {
            addAll(givenNames)
            addAll(addendum?.split(Regex("[\\s-]+")).orEmpty())
            addAll(sureName.split(Regex("\\s+")))
        }.map { token ->
            token.filter { it.isLetterOrDigit() }
        }.filter { it.isNotEmpty() }
    val camel =
        tokens.joinToString("") { token ->
            if (token.all { it.isUpperCase() || it.isDigit() }) {
                token
            } else {
                val lower = token.lowercase()
                lower.replaceFirstChar { it.uppercase() }
            }
        }
    return camel.ifEmpty { "IdentityKeys" }
}

fun placeholderName(text: String): FictionalName {
    val normalized = text.trim().ifBlank { "Identity" }
    return FictionalName(givenNames = listOf(normalized), sureName = "")
}
