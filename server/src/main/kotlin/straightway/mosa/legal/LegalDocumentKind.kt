package straightway.mosa.legal

internal enum class LegalDocumentKind(
    val documentKind: String,
    val resourceDirectory: String,
    val baselineResource: String,
    val defaultProfileId: String,
    val mosaProfileId: String,
    val fallbackProfileId: String,
    val operatorProfileId: String,
    val operatorProfileVersion: String,
) {
    Privacy(
        documentKind = "privacy",
        resourceDirectory = "clearterms/privacy",
        baselineResource = "clearterms/privacy/privacy-gdpr-baseline.yaml",
        defaultProfileId = "privacy-gdpr-default",
        mosaProfileId = "mosa-privacy-gdpr-service",
        fallbackProfileId = "mosa-privacy-fallback-service",
        operatorProfileId = "privacy-operator-service",
        operatorProfileVersion = "1.0.0-draft2",
    ),
    Terms(
        documentKind = "terms",
        resourceDirectory = "clearterms/terms",
        baselineResource = "clearterms/terms/terms-baseline.yaml",
        defaultProfileId = "terms-default",
        mosaProfileId = "mosa-terms-service",
        fallbackProfileId = "mosa-terms-fallback-operator",
        operatorProfileId = "terms-operator-service",
        operatorProfileVersion = "1.0.0-draft2",
    ),
}
