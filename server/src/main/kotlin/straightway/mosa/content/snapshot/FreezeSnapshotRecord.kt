package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = FreezeSnapshotContentRecord::class, name = "content"),
    JsonSubTypes.Type(value = FreezeSnapshotComplaintRecord::class, name = "complaint"),
    JsonSubTypes.Type(value = FreezeSnapshotAdminMessageRecord::class, name = "adminMessage"),
)
sealed interface FreezeSnapshotRecord {
    val id: String
}
