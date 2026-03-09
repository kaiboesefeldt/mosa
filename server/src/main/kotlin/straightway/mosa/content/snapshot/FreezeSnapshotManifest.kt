package straightway.mosa.content.snapshot

import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("ownerGid", "recordCount", "archivePolicyVersion")
data class FreezeSnapshotManifest(
    val ownerGid: String,
    val recordCount: Int,
    val archivePolicyVersion: Int,
)
