package straightway.mosa.content

import kotlinx.serialization.json.Json
import straightway.mosa.content.complaint.CreatePostComplaintRequest
import straightway.mosa.content.complaint.DecidePostComplaintRequest
import straightway.mosa.content.complaint.OpenPostComplaint
import straightway.mosa.content.complaint.OpenPostComplaintsResult
import straightway.mosa.content.complaint.PostComplaintAvailability
import straightway.mosa.content.complaint.PostComplaintAvailabilityResult
import straightway.mosa.content.complaint.PostComplaintDecisionSignaturePayload
import straightway.mosa.content.complaint.PostComplaintSignaturePayload
import straightway.mosa.content.complaint.PostComplaintSignatureVersion
import straightway.mosa.content.complaint.PostComplaintState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PostComplaintContractTest {
    @Test
    fun `complaint signature payload is serialized deterministically`() {
        val payload = PostComplaintSignaturePayload.forComplaint(postId = "post-1", reason = "Spam")

        assertContentEquals(
            """{"v":1,"type":"POST_COMPLAINT","postId":"post-1","reason":"Spam"}""".encodeToByteArray(),
            payload.toCanonicalBytes(),
        )
    }

    @Test
    fun `complaint decision signature payload is serialized deterministically`() {
        val payload =
            PostComplaintDecisionSignaturePayload.forDecision(
                postId = "post-1",
                targetStatus = PostComplaintState.CONFIRMED,
                feedback = "Checked",
            )

        assertContentEquals(
            """{"v":1,"type":"POST_COMPLAINT_DECISION","postId":"post-1","targetStatus":"CONFIRMED","feedback":"Checked"}"""
                .encodeToByteArray(),
            payload.toCanonicalBytes(),
        )
    }

    @Test
    fun `complaint dto serialization contains signature metadata`() {
        val createRequest =
            CreatePostComplaintRequest(
                postId = "post-1",
                reason = "Spam",
                signature = "sig-1",
                signatureVersion = 7,
            )
        val decisionRequest =
            DecidePostComplaintRequest(
                complaintId = "complaint-1",
                postId = "post-1",
                targetStatus = PostComplaintState.REJECTED,
                feedback = "Allowed",
                signature = "sig-2",
                signatureVersion = 8,
            )

        val createJson = Json.encodeToString(createRequest)
        val decisionJson = Json.encodeToString(decisionRequest)

        assertContains(createJson, """"signature":"sig-1"""")
        assertContains(createJson, """"signatureVersion":7""")
        assertContains(decisionJson, """"signature":"sig-2"""")
        assertContains(decisionJson, """"signatureVersion":8""")
    }

    @Test
    fun `complaint dto deserialization keeps explicit signature metadata`() {
        val createRequest =
            Json.decodeFromString<CreatePostComplaintRequest>(
                """{"postId":"post-1","reason":"Spam","signature":"sig-1","signatureVersion":7}""",
            )
        val decisionRequest =
            Json.decodeFromString<DecidePostComplaintRequest>(
                """{"complaintId":"complaint-1","postId":"post-1","targetStatus":"DELETED","feedback":"Removed","signature":"sig-2","signatureVersion":8}""",
            )

        assertEquals("sig-1", createRequest.signature)
        assertEquals(7, createRequest.signatureVersion)
        assertEquals("sig-2", decisionRequest.signature)
        assertEquals(8, decisionRequest.signatureVersion)
    }

    @Test
    fun `complaint dto deserialization remains compatible with missing signature metadata`() {
        val createRequest =
            Json.decodeFromString<CreatePostComplaintRequest>(
                """{"postId":"post-1","reason":"Spam"}""",
            )
        val decisionRequest =
            Json.decodeFromString<DecidePostComplaintRequest>(
                """{"complaintId":"complaint-1","postId":"post-1","targetStatus":"CONFIRMED","feedback":"Checked"}""",
            )

        assertEquals("", createRequest.signature)
        assertEquals(PostComplaintSignatureVersion.V1, createRequest.signatureVersion)
        assertEquals("", decisionRequest.signature)
        assertEquals(PostComplaintSignatureVersion.V1, decisionRequest.signatureVersion)
    }

    @Test
    fun `complaint list result roundtrip keeps entries and paging`() {
        val result =
            OpenPostComplaintsResult(
                entries =
                    listOf(
                        OpenPostComplaint(
                            complaintId = "complaint-1",
                            postId = "post-1",
                            postText = "Post text",
                            postAuthorGid = "author-1",
                            postCreatedAt = 10,
                            complainantGid = "complainant-1",
                            reason = "Spam",
                            createdAt = 20,
                        ),
                    ),
                page = 2,
                hasMore = true,
            )

        val decoded = Json.decodeFromString<OpenPostComplaintsResult>(Json.encodeToString(result))

        assertEquals(result, decoded)
    }

    @Test
    fun `post complaint status result roundtrip keeps status`() {
        val result = PostComplaintAvailabilityResult(postId = "post-1", availability = PostComplaintAvailability.BLOCKED)

        val decoded = Json.decodeFromString<PostComplaintAvailabilityResult>(Json.encodeToString(result))

        assertEquals(result, decoded)
    }
}
