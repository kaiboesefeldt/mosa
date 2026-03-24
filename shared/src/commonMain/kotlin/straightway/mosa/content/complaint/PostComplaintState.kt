package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
enum class PostComplaintState {
    OPEN,
    CONFIRMED,
    REJECTED,
    DELETED,
}
