package straightway.mosa.content.complaint

import kotlinx.serialization.Serializable

@Serializable
data class PostComplaintAvailabilityResult(
    val postId: String,
    val availability: PostComplaintAvailability,
)
