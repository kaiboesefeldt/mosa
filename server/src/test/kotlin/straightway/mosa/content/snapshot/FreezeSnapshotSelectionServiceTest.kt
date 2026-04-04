package straightway.mosa.content.snapshot

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.VectorStore
import straightway.mosa.content.MissingVectorMetadataException
import straightway.mosa.content.StructuredStateContext
import straightway.mosa.content.complaint.PostComplaintRecord
import straightway.mosa.content.complaint.PostComplaintState
import straightway.mosa.content.complaint.PostComplaintStore
import straightway.mosa.content.complaint.PostComplaintStoreFactory
import straightway.mosa.message.AdminMessageRecord
import straightway.mosa.message.AdminMessageStore
import straightway.mosa.message.AdminMessageStoreFactory
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FreezeSnapshotSelectionServiceTest {
    private val vectorStore = mockk<VectorStore>()
    private val context = mockk<StructuredStateContext>()
    private val postComplaintStore = mockk<PostComplaintStore>()
    private val adminMessageStore = mockk<AdminMessageStore>()
    private val sut =
        FreezeSnapshotSelectionService(
            vectorStore = vectorStore,
            structuredStateContext = context,
            postComplaintStoreFactory = PostComplaintStoreFactory { postComplaintStore },
            adminMessageStoreFactory = AdminMessageStoreFactory { adminMessageStore },
        )

    @Test
    fun `selecting one own root post includes that post`() =
        runTest {
            givenOwnContent(
                postDump(id = "root-1", authorGid = "owner", storedAt = 20),
            )
            givenRepliesOnOwnedRoots()
            givenModerationData()

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("root-1"), selected.map { it.id })
        }

    @Test
    fun `selecting one own reply includes that reply`() =
        runTest {
            givenOwnContent(
                postDump(id = "reply-1", authorGid = "owner", storedAt = 20, rootId = "foreign-root"),
            )
            givenRepliesOnOwnedRoots()
            givenModerationData()

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("reply-1"), selected.map { it.id })
        }

    @Test
    fun `selecting a foreign reply on an own root includes that foreign reply`() =
        runTest {
            givenOwnContent(
                postDump(id = "root-1", authorGid = "owner", storedAt = 10),
            )
            givenRepliesOnOwnedRoots(
                rootId = "root-1",
                postDump(id = "reply-1", authorGid = "other", storedAt = 20, rootId = "root-1"),
            )
            givenModerationData()

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("root-1", "reply-1"), selected.map { it.id })
        }

    @Test
    fun `selecting a foreign root excludes that root`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()
            givenModerationData()

            val selected = sut.resolveFor("owner")

            assertEquals(emptyList(), selected)
        }

    @Test
    fun `selecting a foreign reply on a foreign root excludes that reply`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()
            givenModerationData()

            val selected = sut.resolveFor("owner")

            assertEquals(emptyList(), selected)
        }

    @Test
    fun `mixed content returns exactly the expected record ids in deterministic order`() =
        runTest {
            givenOwnContent(
                postDump(id = "own-reply-on-foreign-root", authorGid = "owner", storedAt = 20, rootId = "foreign-root"),
                postDump(id = "own-root", authorGid = "owner", storedAt = 10),
            )
            givenRepliesOnOwnedRoots(
                rootId = "own-root",
                postDump(id = "reply-on-own-root", authorGid = "other", storedAt = 30, rootId = "own-root"),
                postDump(id = "own-reply-on-own-root", authorGid = "owner", storedAt = 40, rootId = "own-root"),
            )
            givenModerationData(
                complaints =
                    listOf(
                        complaintRecord(id = "complaint-1", createdAt = 25),
                    ),
                adminMessages =
                    listOf(
                        adminMessageRecord(id = "message-1", createdAt = 35),
                    ),
            )

            val selected = sut.resolveFor("owner")

            assertEquals(
                listOf("own-root", "own-reply-on-foreign-root", "complaint-1", "reply-on-own-root", "message-1", "own-reply-on-own-root"),
                selected.map { it.id },
            )
            val contentRecord = selected.first() as FreezeSnapshotContentRecord
            assertEquals("public-own-root", contentRecord.authorPublicKey)
            assertEquals("signature-own-root", contentRecord.authorSignature)
            assertEquals(10, contentRecord.signatureVersion)
        }

    @Test
    fun `missing author metadata fails selection`() =
        runTest {
            givenOwnContent(
                VectorStore.PostDump(
                    id = "root-1",
                    text = "text",
                    fields = mapOf(RECEIVED_AT_KEY to "10"),
                ),
            )
            givenRepliesOnOwnedRoots()
            givenModerationData()

            assertFailsWith<MissingVectorMetadataException> {
                sut.resolveFor("owner")
            }
        }

    @Test
    fun `missing received at metadata fails selection`() =
        runTest {
            givenOwnContent(
                VectorStore.PostDump(
                    id = "root-1",
                    text = "text",
                    fields = mapOf(AUTHOR_GID_KEY to "owner"),
                ),
            )
            givenRepliesOnOwnedRoots()
            givenModerationData()

            assertFailsWith<MissingVectorMetadataException> {
                sut.resolveFor("owner")
            }
        }

    @Test
    fun `missing author public key metadata fails selection`() =
        runTest {
            givenOwnContent(
                postDump(id = "root-1", authorGid = "owner", storedAt = 10, extraFields = emptyMap()),
            )
            givenRepliesOnOwnedRoots()
            givenModerationData()

            assertFailsWith<MissingVectorMetadataException> {
                sut.resolveFor("owner")
            }
        }

    @Test
    fun `selection includes complaints from both relevant identity directions and excludes foreign ones`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()
            givenModerationData(
                complaints =
                    listOf(
                        complaintRecord(id = "complaint-own", complainantGid = "owner", postAuthorGid = "other", createdAt = 10),
                        complaintRecord(id = "complaint-about-owner", complainantGid = "other", postAuthorGid = "owner", createdAt = 20),
                    ),
            )

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("complaint-own", "complaint-about-owner"), selected.map { it.id })
            assertEquals(
                listOf("complaint-own", "complaint-about-owner"),
                selected.filterIsInstance<FreezeSnapshotComplaintRecord>().map { it.id },
            )
        }

    @Test
    fun `selection includes admin messages from both relevant identity directions and excludes foreign ones`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()
            givenModerationData(
                adminMessages =
                    listOf(
                        adminMessageRecord(id = "message-to-owner", recipientGid = "owner", senderGid = "admin", createdAt = 10),
                        adminMessageRecord(id = "message-from-owner", recipientGid = "user", senderGid = "owner", createdAt = 20),
                    ),
            )

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("message-to-owner", "message-from-owner"), selected.map { it.id })
            assertEquals(
                listOf("message-to-owner", "message-from-owner"),
                selected.filterIsInstance<FreezeSnapshotAdminMessageRecord>().map { it.id },
            )
        }

    private fun givenOwnContent(vararg posts: VectorStore.PostDump) {
        coEvery { vectorStore.fetchByFieldValues(AUTHOR_GID_KEY, setOf("owner")) } returns posts.toList()
    }

    private fun givenRepliesOnOwnedRoots(
        rootId: String? = null,
        vararg posts: VectorStore.PostDump,
    ) {
        coEvery { vectorStore.fetchByFieldValues(ROOT_ID_KEY, any()) } returns emptyList()
        rootId?.let {
            coEvery { vectorStore.fetchByFieldValues(ROOT_ID_KEY, setOf(it)) } returns posts.toList()
        }
        coEvery { vectorStore.fetchByFieldValues(ROOT_ID_KEY, emptySet()) } returns emptyList()
    }

    private fun givenModerationData(
        complaints: List<PostComplaintRecord> = emptyList(),
        adminMessages: List<AdminMessageRecord> = emptyList(),
    ) {
        io.mockk.every { postComplaintStore.loadForIdentity("owner") } returns complaints
        io.mockk.every { adminMessageStore.loadForIdentity("owner") } returns adminMessages
    }

    private fun postDump(
        id: String,
        authorGid: String,
        storedAt: Long,
        rootId: String? = null,
        extraFields: Map<String, String> =
            mapOf(
                AUTHOR_PUBLIC_KEY_KEY to "public-$id",
                AUTHOR_SIGNATURE_KEY to "signature-$id",
                SIGNATURE_VERSION_KEY to storedAt.toString(),
            ),
    ) = VectorStore.PostDump(
        id = id,
        text = "$id text",
        fields =
            mapOf(
                AUTHOR_GID_KEY to authorGid,
                RECEIVED_AT_KEY to storedAt.toString(),
            ) + extraFields + listOfNotNull(rootId?.let { ROOT_ID_KEY to it }),
    )

    private fun complaintRecord(
        id: String,
        complainantGid: String = "owner",
        postAuthorGid: String = "other",
        createdAt: Long,
    ) = PostComplaintRecord(
        id = id,
        postId = "post-$id",
        complainantGid = complainantGid,
        postAuthorGid = postAuthorGid,
        reason = "reason-$id",
        createdAt = createdAt,
        complainantSignature = "signature-$id",
        complainantSignatureVersion = 1,
        state = PostComplaintState.OPEN,
    )

    private fun adminMessageRecord(
        id: String,
        recipientGid: String = "owner",
        senderGid: String = "admin",
        createdAt: Long,
    ) = AdminMessageRecord(
        id = id,
        recipientGid = recipientGid,
        senderGid = senderGid,
        createdAt = createdAt,
        subject = "subject-$id",
        body = "body-$id",
        expiresAt = createdAt + 100,
    )
}
