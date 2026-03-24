package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
enum class PostComplaintAvailability {
    AVAILABLE,
    OPEN,
    BLOCKED,
}
