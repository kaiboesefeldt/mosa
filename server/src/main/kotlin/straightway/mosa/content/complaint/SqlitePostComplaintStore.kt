package straightway.mosa.content.complaint

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import straightway.mosa.structuredstate.StructuredStateContext

class SqlitePostComplaintStore(
    private val context: StructuredStateContext,
) : PostComplaintStore {
    override fun appendOpen(record: PostComplaintRecord) {
        try {
            insert(record, unreadMarker = null)
        } catch (exception: Throwable) {
            if (exception.message?.contains("UNIQUE constraint failed") == true) {
                throw PostComplaintConflictException("an open complaint already exists for this post")
            }
            throw exception
        }
    }

    override fun restore(record: PostComplaintRecord) {
        context.execute {
            PostComplaintsTable.deleteWhere {
                PostComplaintsTable.id eq record.id
            }
        }
        insert(record, unreadMarker = record.id)
    }

    override fun loadForIdentity(gid: String): List<PostComplaintRecord> =
        context.execute {
            PostComplaintsTable
                .selectAll()
                .where {
                    (PostComplaintsTable.complainantGid eq gid) or
                        (PostComplaintsTable.postAuthorGid eq gid)
                }.orderBy(PostComplaintsTable.createdAt, SortOrder.ASC)
                .map(::toRecord)
        }

    override fun deleteByIds(ids: List<String>): Int {
        if (ids.isEmpty()) {
            return 0
        }
        return context.execute {
            PostComplaintsTable.deleteWhere {
                PostComplaintsTable.id inList ids
            }
        }
    }

    override fun deleteForPost(postId: String): Int =
        context.execute {
            PostComplaintsTable.deleteWhere {
                PostComplaintsTable.postId eq postId
            }
        }

    override fun loadForPost(postId: String): List<PostComplaintRecord> =
        context.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.postId eq postId }
                .orderBy(PostComplaintsTable.createdAt, SortOrder.ASC)
                .map(::toRecord)
        }

    override fun loadLatestForPost(postId: String): PostComplaintRecord? =
        context.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.postId eq postId }
                .orderBy(PostComplaintsTable.createdAt, SortOrder.DESC)
                .limit(1)
                .map(::toRecord)
                .singleOrNull()
        }

    override fun deleteForIdentity(gid: String): Int =
        context.execute {
            PostComplaintsTable.deleteWhere {
                (complainantGid eq gid) or
                    (postAuthorGid eq gid)
            }
        }

    override fun loadById(id: String): PostComplaintRecord? =
        context.execute {
            PostComplaintsTable
                .selectAll()
                .where { PostComplaintsTable.id eq id }
                .limit(1)
                .map(::toRecord)
                .singleOrNull()
        }

    override fun countOpen(): Int =
        context.execute {
            PostComplaintsTable
                .selectAll()
                .where { isVisibleInUnreadQueue() }
                .count()
                .toInt()
        }

    override fun loadPage(
        limit: Int,
        offset: Int,
        includeProcessed: Boolean,
    ): List<PostComplaintRecord> =
        context.execute {
            val query = PostComplaintsTable.selectAll()
            val filteredQuery =
                if (includeProcessed) {
                    query
                } else {
                    query.where { isVisibleInUnreadQueue() }
                }
            filteredQuery
                .orderBy(PostComplaintsTable.createdAt, SortOrder.ASC)
                .limit(limit)
                .offset(offset.toLong())
                .map(::toRecord)
        }

    override fun markAsRead(ids: List<String>): Int {
        if (ids.isEmpty()) {
            return 0
        }
        return context.execute {
            PostComplaintsTable.update(
                where = {
                    PostComplaintsTable.id inList ids
                },
            ) { statement ->
                statement[PostComplaintsTable.unreadMarker] = null
            }
        }
    }

    override fun decide(
        complaintId: String,
        expectedState: PostComplaintState,
        state: PostComplaintState,
        adminGid: String,
        decidedAt: Long,
        feedback: String,
        adminSignature: String,
        adminSignatureVersion: Int,
    ): Boolean =
        context.execute {
            PostComplaintsTable.update(
                where = {
                    (PostComplaintsTable.id eq complaintId) and
                        (PostComplaintsTable.state eq expectedState)
                },
            ) { statement ->
                statement[PostComplaintsTable.state] = state
                statement[decidedByAdminGid] = adminGid
                statement[PostComplaintsTable.decidedAt] = decidedAt
                statement[PostComplaintsTable.feedback] = feedback
                statement[PostComplaintsTable.adminSignature] = adminSignature
                statement[PostComplaintsTable.adminSignatureVersion] = adminSignatureVersion
                statement[openMarker] = null
                statement[PostComplaintsTable.unreadMarker] = null
            } == 1
        }

    private fun insert(
        record: PostComplaintRecord,
        unreadMarker: String?,
    ) {
        context.execute {
            PostComplaintsTable.insert { statement ->
                statement[id] = record.id
                statement[postId] = record.postId
                statement[complainantGid] = record.complainantGid
                statement[postAuthorGid] = record.postAuthorGid
                statement[reason] = record.reason
                statement[createdAt] = record.createdAt
                statement[complainantSignature] = record.complainantSignature
                statement[complainantSignatureVersion] = record.complainantSignatureVersion
                statement[state] = record.state
                statement[decidedByAdminGid] = record.decidedByAdminGid
                statement[decidedAt] = record.decidedAt
                statement[feedback] = record.feedback
                statement[adminSignature] = record.adminSignature
                statement[adminSignatureVersion] = record.adminSignatureVersion
                statement[openMarker] = if (record.state == PostComplaintState.OPEN) OPEN_MARKER else null
                statement[PostComplaintsTable.unreadMarker] = unreadMarker
            }
        }
    }

    private fun isVisibleInUnreadQueue() =
        (PostComplaintsTable.openMarker eq OPEN_MARKER) or
            PostComplaintsTable.unreadMarker.isNotNull()

    private fun toRecord(row: ResultRow): PostComplaintRecord =
        PostComplaintRecord(
            id = row[PostComplaintsTable.id],
            postId = row[PostComplaintsTable.postId],
            complainantGid = row[PostComplaintsTable.complainantGid],
            postAuthorGid = row[PostComplaintsTable.postAuthorGid],
            reason = row[PostComplaintsTable.reason],
            createdAt = row[PostComplaintsTable.createdAt],
            complainantSignature = row[PostComplaintsTable.complainantSignature],
            complainantSignatureVersion = row[PostComplaintsTable.complainantSignatureVersion],
            state = row[PostComplaintsTable.state],
            decidedByAdminGid = row[PostComplaintsTable.decidedByAdminGid],
            decidedAt = row[PostComplaintsTable.decidedAt],
            feedback = row[PostComplaintsTable.feedback],
            adminSignature = row[PostComplaintsTable.adminSignature],
            adminSignatureVersion = row[PostComplaintsTable.adminSignatureVersion],
        )

    private companion object {
        const val OPEN_MARKER = "OPEN"
    }
}
