package straightway.mosa.legal.clearterms

data class ClearTermsDefaultProfile(
    val id: String,
    val version: String,
    val baseline: ClearTermsReference,
    val values: Map<String, ClearTermsYamlNode.ScalarNode>,
)
