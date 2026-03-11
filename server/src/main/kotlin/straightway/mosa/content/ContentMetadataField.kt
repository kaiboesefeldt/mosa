package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_GID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_PUBLIC_KEY_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.AUTHOR_SIGNATURE_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.RECEIVED_AT_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.ROOT_ID_KEY
import straightway.mosa.vectorstore.ContentMetadataKeys.SIGNATURE_VERSION_KEY

internal enum class ContentMetadataField(
    val key: String,
) {
    RootId(ROOT_ID_KEY),
    AuthorGid(AUTHOR_GID_KEY),
    AuthorPublicKey(AUTHOR_PUBLIC_KEY_KEY),
    AuthorSignature(AUTHOR_SIGNATURE_KEY),
    SignatureVersion(SIGNATURE_VERSION_KEY),
    ReceivedAt(RECEIVED_AT_KEY),
}

internal fun Map<String, String>.requireContentMetadata(
    entryId: String,
    field: ContentMetadataField,
): String = get(field.key) ?: missingContentMetadata(entryId, field)

private fun missingContentMetadata(
    entryId: String,
    field: ContentMetadataField,
): Nothing {
    log.error { "Vector store entry $entryId is missing metadata field ${field.key}" }
    throw MissingVectorMetadataException(entryId, field.key)
}

private val log = KotlinLogging.logger { }
