package straightway.mosa.client.content

data class CachedPostResolution(
    val postsById: Map<String, CachedPost>,
    val missingIds: List<String>,
)
