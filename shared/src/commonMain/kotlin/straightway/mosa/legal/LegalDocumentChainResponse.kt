package straightway.mosa.legal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LegalDocumentChainResponse(
    val baseline: JsonObject,
    val profiles: List<JsonObject>,
)
