package straightway.mosa.legal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import straightway.mosa.legal.clearterms.ClearTermsProfile
import straightway.mosa.legal.clearterms.ClearTermsTextBlock
import straightway.mosa.legal.clearterms.ClearTermsYamlCanonicalJsonConverter
import straightway.mosa.legal.clearterms.parse.ClearTermsJsonNode

internal data class LegalDocuments(
    val baselineCanonicalJson: String,
    val profileCanonicalJsons: List<String>,
    val profileCanonicalJsonById: Map<String, String>,
) {
    val lastProfileCanonicalJson: String get() = profileCanonicalJsons.last()

    fun requiredProfileCanonicalJson(profileId: String): String =
        profileCanonicalJsonById[profileId]
            ?: throw IllegalStateException("Missing required legal profile '$profileId'")

    fun toDocumentChainResponse(): LegalDocumentChainResponse =
        LegalDocumentChainResponse(
            baseline = jsonObjectOf(baselineCanonicalJson),
            profiles = profileCanonicalJsons.map(::jsonObjectOf),
        )
}

internal data class MandatoryChainContext(
    val baselineYaml: String,
    val profileYamls: List<String>,
    val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
    val profileCanonicalJsons: List<String>,
    val lastProfile: ProfileMetadata,
    val availableProfileVersions: Map<String, List<String>>,
) {
    fun availableProfileVersionsText(): String =
        availableProfileVersions.entries
            .sortedBy { (id, _) -> id }
            .joinToString("; ") { (id, versions) -> "$id=[${versions.joinToString(", ")}]" }
}

internal data class FullChainContext(
    val conversion: ClearTermsYamlCanonicalJsonConverter.ConversionResult,
    val profiles: List<ClearTermsProfile>,
    val profileCanonicalJsons: List<String>,
)

internal data class MergeResult(
    val effectiveTexts: Map<String, List<ClearTermsTextBlock>>,
    val mergedValues: Map<String, ClearTermsJsonNode.ScalarNode>,
)

internal data class ArtifactReference(
    val id: String,
    val version: String,
)

internal data class BaselineMetadata(
    val id: String,
    val version: String,
) {
    val reference: ArtifactReference = ArtifactReference(id, version)
}

internal data class ProfileMetadata(
    val id: String,
    val version: String,
    val parent: ArtifactReference,
) {
    val reference: ArtifactReference = ArtifactReference(id, version)
}

internal data class OperatorProfileMetadata(
    val id: String,
    val version: String,
    val referencedParentId: String,
    val referencedParentVersion: String,
) {
    companion object {
        fun missing(path: String): OperatorProfileMetadata =
            OperatorProfileMetadata(
                id = path,
                version = "unknown",
                referencedParentId = "unknown",
                referencedParentVersion = "unknown",
            )
    }
}

internal data class ResourceProfileCandidate(
    val path: String,
    val yaml: String,
)

internal fun ClearTermsYamlCanonicalJsonConverter.CanonicalArtifact.profileId(): String =
    (json.entries.getValue("id") as ClearTermsJsonNode.ScalarNode.StringNode).value

private fun jsonObjectOf(value: String): JsonObject = Json.parseToJsonElement(value).jsonObject
