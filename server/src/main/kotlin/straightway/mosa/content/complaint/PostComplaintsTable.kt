package straightway.mosa.content.complaint

import org.jetbrains.exposed.v1.core.Table

object PostComplaintsTable : Table("post_complaints") {
    val id = varchar("id", length = 128)
    val postId = varchar("post_id", length = 128)
    val complainantGid = varchar("complainant_gid", length = 128)
    val postAuthorGid = varchar("post_author_gid", length = 128)
    val reason = text("reason")
    val createdAt = long("created_at")
    val complainantSignature = text("complainant_signature")
    val complainantSignatureVersion = integer("complainant_signature_version")
    val state = enumerationByName("state", length = 32, klass = PostComplaintState::class)
    val decidedByAdminGid = varchar("decided_by_admin_gid", length = 128).nullable()
    val decidedAt = long("decided_at").nullable()
    val feedback = text("feedback").nullable()
    val adminSignature = text("admin_signature").nullable()
    val adminSignatureVersion = integer("admin_signature_version").nullable()
    val openMarker = varchar("open_marker", length = 16).nullable()
    val unreadMarker = varchar("unread_marker", length = 128).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(postId, openMarker)
    }
}
