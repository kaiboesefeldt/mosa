package straightway.mosa.content.snapshot

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.VectorStore
import straightway.mosa.content.MissingVectorMetadataException
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
    private val sut = FreezeSnapshotSelectionService(vectorStore)

    @Test
    fun `selecting one own root post includes that post`() =
        runTest {
            givenOwnContent(
                postDump(id = "root-1", authorGid = "owner", storedAt = 20),
            )
            givenRepliesOnOwnedRoots()

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

            val selected = sut.resolveFor("owner")

            assertEquals(listOf("root-1", "reply-1"), selected.map { it.id })
        }

    @Test
    fun `selecting a foreign root excludes that root`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()

            val selected = sut.resolveFor("owner")

            assertEquals(emptyList(), selected)
        }

    @Test
    fun `selecting a foreign reply on a foreign root excludes that reply`() =
        runTest {
            givenOwnContent()
            givenRepliesOnOwnedRoots()

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

            val selected = sut.resolveFor("owner")

            assertEquals(
                listOf("own-root", "own-reply-on-foreign-root", "reply-on-own-root", "own-reply-on-own-root"),
                selected.map { it.id },
            )
            assertEquals("public-own-root", selected.first().authorPublicKey)
            assertEquals("signature-own-root", selected.first().authorSignature)
            assertEquals(10, selected.first().signatureVersion)
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

            assertFailsWith<MissingVectorMetadataException> {
                sut.resolveFor("owner")
            }
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
}
