package straightway.mosa.legal.clearterms

class LegalService(
    private val converter: ClearTermsYamlCanonicalJsonConverter = ClearTermsYamlCanonicalJsonConverter(),
) {
    private val documents: PrivacyLegalDocuments = loadPrivacyDocuments()

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val baselineYaml = readRequiredResource(BASELINE_RESOURCE)
        val defaultYaml = readRequiredResource(DEFAULT_RESOURCE)
        val mosaYaml = readRequiredResource(MOSA_RESOURCE)
        val conversionResult = converter.convert(baselineYaml, listOf(defaultYaml, mosaYaml))
        return PrivacyLegalDocuments(
            baselineCanonicalJson = conversionResult.baseline.canonicalJson,
            defaultCanonicalJson = conversionResult.profiles[0].canonicalJson,
            mosaCanonicalJson = conversionResult.profiles[1].canonicalJson,
        )
    }

    private fun readRequiredResource(path: String): String =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("Missing resource '$path'")

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
    )

    private companion object {
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val DEFAULT_RESOURCE = "clearterms/privacy/privacy-gdpr-default.yaml"
        private const val MOSA_RESOURCE = "clearterms/privacy/mosa-privacy-gdpr-service.yaml"
    }
}
