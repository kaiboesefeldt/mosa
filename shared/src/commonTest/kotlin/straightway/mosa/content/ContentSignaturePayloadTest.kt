package straightway.mosa.content

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ContentSignaturePayloadTest {
    @Test
    fun `root post payload is serialized deterministically`() {
        val payload = ContentSignaturePayload.forPost("Hello")

        assertContentEquals(
            """{"v":1,"kind":"post","text":"Hello","rootId":""}""".encodeToByteArray(),
            payload.toCanonicalBytes(),
        )
    }

    @Test
    fun `reply payload contains root id`() {
        val payload = ContentSignaturePayload.forReply("Hello", "root-123")

        assertContentEquals(
            """{"v":1,"kind":"reply","text":"Hello","rootId":"root-123"}""".encodeToByteArray(),
            payload.toCanonicalBytes(),
        )
    }

    @Test
    fun `root post and reply produce different canonical bytes`() {
        val postBytes = ContentSignaturePayload.forPost("Hello").toCanonicalBytes()
        val replyBytes = ContentSignaturePayload.forReply("Hello", "root-123").toCanonicalBytes()

        assertNotEquals(postBytes.decodeToString(), replyBytes.decodeToString())
    }

    @Test
    fun `root posts keep empty root id stable`() {
        val firstBytes = ContentSignaturePayload.forPost("Hello").toCanonicalBytes()
        val secondBytes =
            ContentSignaturePayload(
                version = ContentSignatureVersion.V1,
                kind = ContentKind.POST,
                text = "Hello",
                rootId = "",
            ).toCanonicalBytes()

        assertContentEquals(firstBytes, secondBytes)
    }

    @Test
    fun `content signature payload can be decoded from canonical json`() {
        val payload =
            Json.decodeFromString<ContentSignaturePayload>(
                """{"v":1,"kind":"reply","text":"Hello","rootId":"root-123"}""",
            )

        assertEquals(ContentSignatureVersion.V1, payload.version)
        assertEquals(ContentKind.REPLY, payload.kind)
        assertEquals("Hello", payload.text)
        assertEquals("root-123", payload.rootId)
    }

    @Test
    fun `dto serialization contains new signature fields`() {
        val publishRequest = PublishRequest(text = "Hello", signature = "sig-1", signatureVersion = 7)
        val fetchEntry =
            FetchResult.Entry(
                id = "entry-1",
                content = "Hello",
                authorGID = "gid-1",
                storedAt = 42,
                rootId = "root-1",
                authorPublicKey = "pub-1",
                authorSignature = "sig-1",
                signatureVersion = 7,
            )
        val queryEntry =
            QueryResult.Entry(
                id = "entry-1",
                content = "Hello",
                similarity = 0.9f,
                authorGID = "gid-1",
                storedAt = 42,
                rootId = "root-1",
                authorPublicKey = "pub-1",
                authorSignature = "sig-1",
                signatureVersion = 7,
            )

        val publishJson = Json.encodeToString(publishRequest)
        val fetchJson = Json.encodeToString(fetchEntry)
        val queryJson = Json.encodeToString(queryEntry)

        assertContains(publishJson, """"signature":"sig-1"""")
        assertContains(publishJson, """"signatureVersion":7""")
        assertContains(fetchJson, """"authorPublicKey":"pub-1"""")
        assertContains(fetchJson, """"authorSignature":"sig-1"""")
        assertContains(fetchJson, """"signatureVersion":7""")
        assertContains(queryJson, """"authorPublicKey":"pub-1"""")
        assertContains(queryJson, """"authorSignature":"sig-1"""")
        assertContains(queryJson, """"signatureVersion":7""")
    }

    @Test
    fun `dto deserialization keeps explicit signature fields`() {
        val publishRequest =
            Json.decodeFromString<PublishRequest>(
                """{"text":"Hello","signature":"sig-1","signatureVersion":7}""",
            )
        val fetchEntry =
            Json.decodeFromString<FetchResult.Entry>(
                """{"id":"entry-1","content":"Hello","authorGID":"gid-1","storedAt":42,"rootId":"root-1","authorPublicKey":"pub-1","authorSignature":"sig-1","signatureVersion":7}""",
            )
        val queryEntry =
            Json.decodeFromString<QueryResult.Entry>(
                """{"id":"entry-1","content":"Hello","similarity":0.9,"authorGID":"gid-1","storedAt":42,"rootId":"root-1","authorPublicKey":"pub-1","authorSignature":"sig-1","signatureVersion":7}""",
            )

        assertEquals("sig-1", publishRequest.signature)
        assertEquals(7, publishRequest.signatureVersion)
        assertEquals("pub-1", fetchEntry.authorPublicKey)
        assertEquals("sig-1", fetchEntry.authorSignature)
        assertEquals(7, fetchEntry.signatureVersion)
        assertEquals("pub-1", queryEntry.authorPublicKey)
        assertEquals("sig-1", queryEntry.authorSignature)
        assertEquals(7, queryEntry.signatureVersion)
    }

    @Test
    fun `dto deserialization remains compatible with missing signature fields`() {
        val publishRequest =
            Json.decodeFromString<PublishRequest>(
                """{"text":"Hello"}""",
            )
        val fetchEntry =
            Json.decodeFromString<FetchResult.Entry>(
                """{"id":"entry-1","content":"Hello","authorGID":"gid-1","storedAt":42}""",
            )
        val queryEntry =
            Json.decodeFromString<QueryResult.Entry>(
                """{"id":"entry-1","content":"Hello","similarity":0.9,"authorGID":"gid-1","storedAt":42}""",
            )

        assertEquals("", publishRequest.signature)
        assertEquals(ContentSignatureVersion.V1, publishRequest.signatureVersion)
        assertEquals(null, fetchEntry.rootId)
        assertEquals("", fetchEntry.authorPublicKey)
        assertEquals("", fetchEntry.authorSignature)
        assertEquals(ContentSignatureVersion.V1, fetchEntry.signatureVersion)
        assertEquals(null, queryEntry.rootId)
        assertEquals("", queryEntry.authorPublicKey)
        assertEquals("", queryEntry.authorSignature)
        assertEquals(ContentSignatureVersion.V1, queryEntry.signatureVersion)
    }

    @Test
    fun `fetch result roundtrip keeps signature metadata`() {
        val result =
            FetchResult(
                entries =
                    listOf(
                        FetchResult.Entry(
                            id = "entry-1",
                            content = "Hello",
                            authorGID = "gid-1",
                            storedAt = 42,
                            rootId = "root-1",
                            authorPublicKey = "pub-1",
                            authorSignature = "sig-1",
                            signatureVersion = 7,
                        ),
                    ),
            )

        val decoded = Json.decodeFromString<FetchResult>(Json.encodeToString(result))

        assertEquals(result, decoded)
    }

    @Test
    fun `query result roundtrip keeps signature metadata`() {
        val result =
            QueryResult(
                entries =
                    listOf(
                        QueryResult.Entry(
                            id = "entry-1",
                            content = "Hello",
                            similarity = 0.9f,
                            authorGID = "gid-1",
                            storedAt = 42,
                            rootId = "root-1",
                            authorPublicKey = "pub-1",
                            authorSignature = "sig-1",
                            signatureVersion = 7,
                        ),
                    ),
                queryOnId = "query-on-1",
            )

        val decoded = Json.decodeFromString<QueryResult>(Json.encodeToString(result))

        assertEquals(result, decoded)
    }
}
