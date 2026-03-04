package straightway.mosa.client.legal

data class LegalPrivacyPolicyDocument(
    val html: String,
    val uncriticalTextByBlockId: Map<String, String>,
)
