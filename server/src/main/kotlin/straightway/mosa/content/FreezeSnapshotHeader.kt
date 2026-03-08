package straightway.mosa.content

import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.time.Instant

@JsonPropertyOrder("formatVersion", "podId", "keyId", "createdAt", "expiresAt", "nonce")
data class FreezeSnapshotHeader(
    val formatVersion: Int,
    val podId: String,
    val keyId: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val nonce: String,
)
