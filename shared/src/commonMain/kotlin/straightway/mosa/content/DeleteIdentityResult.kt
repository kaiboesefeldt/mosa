package straightway.mosa.content

import kotlinx.serialization.Serializable

@Serializable
data class DeleteIdentityResult(
    val deletedPostIds: List<String>,
    val deletedComplaintCount: Int,
    val deletedAdminMessageCount: Int,
)
