package straightway.mosa.legal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class LegalDocumentChainResponseTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `serializes baseline separately from ordered profile chain`() {
        val response =
            LegalDocumentChainResponse(
                baseline =
                    buildJsonObject {
                        put("kind", "baseline")
                        put("id", "privacy-baseline")
                    },
                profiles =
                    listOf(
                        buildJsonObject {
                            put("kind", "profile")
                            put("id", "default-profile")
                        },
                        buildJsonObject {
                            put("kind", "profile")
                            put("id", "operator-profile")
                        },
                    ),
            )

        val serialized = json.encodeToString(LegalDocumentChainResponse.serializer(), response)

        assertEquals(
            """{"baseline":{"kind":"baseline","id":"privacy-baseline"},"profiles":[{"kind":"profile","id":"default-profile"},{"kind":"profile","id":"operator-profile"}]}""",
            serialized,
        )
    }
}
