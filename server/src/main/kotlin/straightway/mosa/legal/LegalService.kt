package straightway.mosa.legal

import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter

class LegalService(
    private val converter: ClearTermsYamlCanonicalJsonConverter = ClearTermsYamlCanonicalJsonConverter(),
    private val operatorProfilePath: java.nio.file.Path,
) {
    private val documents: PrivacyLegalDocuments = loadPrivacyDocuments()

    fun privacyBaselineCanonicalJson(): String = documents.baselineCanonicalJson

    fun privacyDefaultCanonicalJson(): String = documents.defaultCanonicalJson

    fun privacyMosaCanonicalJson(): String = documents.mosaCanonicalJson

    fun privacyServiceCanonicalJson(): String = documents.serviceCanonicalJson

    private fun loadPrivacyDocuments(): PrivacyLegalDocuments {
        val baselineYaml = readRequiredResource(BASELINE_RESOURCE)
        val defaultYaml = readRequiredResource(DEFAULT_RESOURCE)
        val mosaYaml = readRequiredResource(MOSA_RESOURCE)
        val operatorYaml = readRequiredOperatorProfile(operatorProfilePath)
        val conversionResult = converter.convert(baselineYaml, listOf(defaultYaml, mosaYaml, operatorYaml))
        return PrivacyLegalDocuments(
            baselineCanonicalJson = conversionResult.baseline.canonicalJson,
            defaultCanonicalJson = conversionResult.profiles[0].canonicalJson,
            mosaCanonicalJson = conversionResult.profiles[1].canonicalJson,
            serviceCanonicalJson = conversionResult.profiles[2].canonicalJson,
        )
    }

    private fun readRequiredResource(path: String): String =
        javaClass.classLoader
            .getResourceAsStream(path)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: throw IllegalStateException("Missing resource '$path'")

    private fun readRequiredOperatorProfile(path: java.nio.file.Path): String {
        val normalizedPath = path.toAbsolutePath().normalize()
        if (!java.nio.file.Files
                .exists(normalizedPath)
        ) {
            throw IllegalStateException("Missing required legal profile file '$normalizedPath'")
        }
        return java.nio.file.Files
            .readString(normalizedPath, Charsets.UTF_8)
    }

    private data class PrivacyLegalDocuments(
        val baselineCanonicalJson: String,
        val defaultCanonicalJson: String,
        val mosaCanonicalJson: String,
        val serviceCanonicalJson: String,
    )

    private companion object {
        private const val BASELINE_RESOURCE = "clearterms/privacy/privacy-gdpr-baseline.yaml"
        private const val DEFAULT_RESOURCE = "clearterms/privacy/privacy-gdpr-default.yaml"
        private const val MOSA_RESOURCE = "clearterms/privacy/mosa-privacy-gdpr-service.yaml"
    }
}
