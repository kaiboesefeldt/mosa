package straightway.mosa.message

import org.jetbrains.exposed.v1.core.Table

object AdminMessagesTable : Table("admin_messages") {
    val id = varchar("id", length = 128)
    val recipientGid = varchar("recipient_gid", length = 128)
    val senderGid = varchar("sender_gid", length = 128)
    val createdAt = long("created_at")
    val subject = text("subject")
    val body = text("body")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(id)
}
