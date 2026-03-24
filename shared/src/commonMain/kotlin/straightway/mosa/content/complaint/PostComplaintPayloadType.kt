package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
enum class PostComplaintPayloadType {
    POST_COMPLAINT,
    POST_COMPLAINT_DECISION,
}
