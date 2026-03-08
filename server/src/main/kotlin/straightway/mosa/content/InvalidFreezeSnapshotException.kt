package straightway.mosa.content

class InvalidFreezeSnapshotException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
