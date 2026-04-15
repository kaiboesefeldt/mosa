package straightway.mosa.identity.privacy.snapshot

data class FreezeSnapshot(
    val header: FreezeSnapshotHeader,
    val manifest: FreezeSnapshotManifest,
    val records: List<FreezeSnapshotRecord>,
)
