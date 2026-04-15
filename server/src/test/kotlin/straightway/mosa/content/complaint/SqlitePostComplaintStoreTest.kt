package straightway.mosa.content.complaint

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.structuredstate.StructuredStateDatabase
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SqlitePostComplaintStoreTest {
    @Test
    fun `appendOpen stores latest complaint for post`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)
                val record =
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    )

                sut.appendOpen(record)

                assertEquals(record, sut.loadLatestForPost("post-1"))
                assertEquals(1, sut.countOpen())
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `appendOpen rejects second open complaint for same post`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertFailsWith<PostComplaintConflictException> {
                    sut.appendOpen(
                        PostComplaintRecord(
                            id = "complaint-2",
                            postId = "post-1",
                            complainantGid = "gid-2",
                            postAuthorGid = "author-1",
                            reason = "Abuse",
                            createdAt = 101,
                            complainantSignature = "sig-2",
                            complainantSignatureVersion = 1,
                            state = PostComplaintState.OPEN,
                        ),
                    )
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadForIdentity returns complaints from both complaint directions in ascending order`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-own",
                        postId = "post-1",
                        complainantGid = "owner",
                        postAuthorGid = "other",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-about-owner",
                        postId = "post-2",
                        complainantGid = "other",
                        postAuthorGid = "owner",
                        reason = "Abuse",
                        createdAt = 101,
                        complainantSignature = "sig-2",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-foreign",
                        postId = "post-3",
                        complainantGid = "foreign",
                        postAuthorGid = "another",
                        reason = "Noise",
                        createdAt = 102,
                        complainantSignature = "sig-3",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertEquals(listOf("complaint-own", "complaint-about-owner"), sut.loadForIdentity("owner").map(PostComplaintRecord::id))
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `deleteByIds removes exactly matching complaints`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-2",
                        postId = "post-2",
                        complainantGid = "gid-2",
                        postAuthorGid = "author-2",
                        reason = "Abuse",
                        createdAt = 101,
                        complainantSignature = "sig-2",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertEquals(1, sut.deleteByIds(listOf("complaint-1")))
                assertEquals(null, sut.loadById("complaint-1"))
                assertEquals("complaint-2", sut.loadById("complaint-2")?.id)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `restored decided complaint appears once as unread in moderation queue`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.restore(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.CONFIRMED,
                        decidedByAdminGid = "admin-1",
                        decidedAt = 110,
                        feedback = "Checked",
                        adminSignature = "admin-sig",
                        adminSignatureVersion = 2,
                    ),
                )

                assertEquals(1, sut.countOpen())
                assertEquals(
                    listOf("complaint-1"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = false).map(PostComplaintRecord::id),
                )

                sut.markAsRead(listOf("complaint-1"))

                assertEquals(0, sut.countOpen())
                assertEquals(emptyList(), sut.loadPage(limit = 10, offset = 0, includeProcessed = false))
                assertEquals(
                    listOf("complaint-1"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = true).map(PostComplaintRecord::id),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `decide removes complaint from open page and allows later open complaint for same post`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-2",
                        postId = "post-2",
                        complainantGid = "gid-2",
                        postAuthorGid = "author-2",
                        reason = "Abuse",
                        createdAt = 101,
                        complainantSignature = "sig-2",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertEquals(
                    listOf("complaint-1", "complaint-2"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = false).map(PostComplaintRecord::id),
                )

                assertEquals(
                    true,
                    sut.decide(
                        complaintId = "complaint-1",
                        expectedState = PostComplaintState.OPEN,
                        state = PostComplaintState.CONFIRMED,
                        adminGid = "admin-1",
                        decidedAt = 200,
                        feedback = "Checked",
                        adminSignature = "admin-sig",
                        adminSignatureVersion = 1,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-3",
                        postId = "post-1",
                        complainantGid = "gid-3",
                        postAuthorGid = "author-1",
                        reason = "Still bad",
                        createdAt = 102,
                        complainantSignature = "sig-3",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertEquals(
                    listOf("complaint-2", "complaint-3"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = false).map(PostComplaintRecord::id),
                )
                assertEquals(PostComplaintState.CONFIRMED, sut.loadById("complaint-1")?.state)
                assertEquals(2, sut.countOpen())
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `loadPage can include processed complaints and decide can change closed state`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "gid-1",
                        postAuthorGid = "author-1",
                        reason = "Spam",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                assertEquals(
                    true,
                    sut.decide(
                        complaintId = "complaint-1",
                        expectedState = PostComplaintState.OPEN,
                        state = PostComplaintState.REJECTED,
                        adminGid = "admin-1",
                        decidedAt = 200,
                        feedback = "Allowed",
                        adminSignature = "admin-sig",
                        adminSignatureVersion = 1,
                    ),
                )
                assertEquals(
                    true,
                    sut.decide(
                        complaintId = "complaint-1",
                        expectedState = PostComplaintState.REJECTED,
                        state = PostComplaintState.CONFIRMED,
                        adminGid = "admin-2",
                        decidedAt = 201,
                        feedback = "Actually justified",
                        adminSignature = "admin-sig-2",
                        adminSignatureVersion = 1,
                    ),
                )

                assertEquals(
                    listOf("complaint-1"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = true).map(PostComplaintRecord::id),
                )
                assertEquals(PostComplaintState.CONFIRMED, sut.loadById("complaint-1")?.state)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `deleteForIdentity removes complaints by complainant or post author gid`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)
                val sut = SqlitePostComplaintStore(database)

                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-1",
                        postId = "post-1",
                        complainantGid = "target-gid",
                        postAuthorGid = "author-1",
                        reason = "Own complaint",
                        createdAt = 100,
                        complainantSignature = "sig-1",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-2",
                        postId = "post-2",
                        complainantGid = "other-gid",
                        postAuthorGid = "target-gid",
                        reason = "Complaint on target author",
                        createdAt = 101,
                        complainantSignature = "sig-2",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )
                sut.appendOpen(
                    PostComplaintRecord(
                        id = "complaint-3",
                        postId = "post-3",
                        complainantGid = "other-gid",
                        postAuthorGid = "author-3",
                        reason = "Unaffected",
                        createdAt = 102,
                        complainantSignature = "sig-3",
                        complainantSignatureVersion = 1,
                        state = PostComplaintState.OPEN,
                    ),
                )

                val deletedCount = sut.deleteForIdentity("target-gid")

                assertEquals(2, deletedCount)
                assertEquals(
                    listOf("complaint-3"),
                    sut.loadPage(limit = 10, offset = 0, includeProcessed = true).map(PostComplaintRecord::id),
                )
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `appendOpen rolls back with outer database transaction`() =
        runTest {
            val directory = Files.createTempDirectory("sqlite-post-complaint-store")
            val path = directory.resolve("mosa_db.sqlite")

            try {
                val database = StructuredStateDatabase(path)
                database.initialize(PostComplaintsTable)

                assertFailsWith<IllegalStateException> {
                    database.inTransaction { context ->
                        val sut = SqlitePostComplaintStore(context)
                        sut.appendOpen(
                            PostComplaintRecord(
                                id = "complaint-1",
                                postId = "post-1",
                                complainantGid = "gid-1",
                                postAuthorGid = "author-1",
                                reason = "Spam",
                                createdAt = 100,
                                complainantSignature = "sig-1",
                                complainantSignatureVersion = 1,
                                state = PostComplaintState.OPEN,
                            ),
                        )
                        throw IllegalStateException("boom")
                    }
                }

                val persisted = SqlitePostComplaintStore(database).loadPage(limit = 10, offset = 0, includeProcessed = true)

                assertEquals(emptyList(), persisted)
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }
}
