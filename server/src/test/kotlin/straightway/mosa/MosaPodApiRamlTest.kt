package straightway.mosa

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertTrue

class MosaPodApiRamlTest {
    private val raml = Files.readString(Path.of("..", "spec", "mosa-pod-api.raml"))

    @Test
    fun `publish and reply request types document signature fields`() {
        assertContains("PublishRequest", "signature:")
        assertContains("PublishRequest", "signatureVersion:")
        assertContains("ReplyRequest", "content: PublishRequest")
    }

    @Test
    fun `fetch and query result entry types document signature fields`() {
        assertContains("QueryResultEntry", "authorPublicKey: string")
        assertContains("QueryResultEntry", "authorSignature: string")
        assertContains("QueryResultEntry", "signatureVersion:")
        assertContains("FetchResultEntry", "authorPublicKey: string")
        assertContains("FetchResultEntry", "authorSignature: string")
        assertContains("FetchResultEntry", "signatureVersion:")
    }

    @Test
    fun `publish and reply endpoints document signature validation errors`() {
        assertContains("/publish", "Invalid request (e.g., malformed signature encoding or text exceeds 1800 characters)")
        assertContains("/publish", "Unsupported signature version or signature verification failed")
        assertContains("/reply", "Root not found, target is not a root post, malformed signature encoding, or text exceeds 1800 characters")
        assertContains("/reply", "Unsupported signature version or signature verification failed")
    }

    @Test
    fun `legal imprint endpoint and document types are documented`() {
        assertContains("/legal", "/imprint:")
        assertContains("/legal", "displayName: Get Imprint Document")
        assertContains("ImprintDocument", "type: ImprintDocument")
        assertContains("ImprintProviderBlock", "ImprintProviderBlock:")
        assertContains("ImprintDocument", "additionalMarkdown?: string")
    }

    private fun assertContains(
        sectionAnchor: String,
        expected: String,
    ) {
        assertTrue(
            raml.contains(expected),
            "Expected RAML near '$sectionAnchor' to contain '$expected'",
        )
    }
}
