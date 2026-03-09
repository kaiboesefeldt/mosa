package straightway.mosa.content.snapshot

class InvalidFreezeSnapshotException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
