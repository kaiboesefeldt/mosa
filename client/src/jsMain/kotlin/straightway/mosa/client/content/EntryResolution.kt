package straightway.mosa.client.content

data class EntryResolution(
    val resolved: List<CachedPost>,
    val missingIds: List<String>,
)