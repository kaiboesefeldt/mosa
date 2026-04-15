package straightway.mosa.identity.privacy.snapshot

data class FreezeSnapshotSizePolicy(
    val maxSnapshotBytes: Int = 1_048_576,
    val maxEncryptedPayloadBytes: Int = 1_048_576,
    val maxDecryptedPayloadBytes: Int = 1_048_576,
    val maxRecordCount: Int = 1_000,
)
