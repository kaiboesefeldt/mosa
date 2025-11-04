package straightway.mosa.content

class MissingVectorMetadataException(
    val entryId: String,
    val fieldName: String,
) : RuntimeException("Stored content is missing required metadata.")
