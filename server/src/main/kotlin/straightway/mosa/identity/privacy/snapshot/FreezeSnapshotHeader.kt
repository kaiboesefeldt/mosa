package straightway.mosa.identity.privacy.snapshot

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.time.Instant

@JsonPropertyOrder("formatVersion", "podId", "keyId", "createdAt", "nonce")
data class FreezeSnapshotHeader(
    val formatVersion: Int,
    val podId: String,
    val keyId: String,
    val createdAt: Instant,
    val nonce: String,
)
