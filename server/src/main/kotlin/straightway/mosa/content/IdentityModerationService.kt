package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.config.contains
import java.nio.file.Files
import java.util.Base64

class IdentityModerationService(
    private val adminAllowList: AutoRefreshingFileBackedContent<AdminAllowList>,
    private val identityBlockList: AutoRefreshingFileBackedContent<IdentityBlockList>,
    private val identityDeleter: IdentityDeleter,
    private val expectedGidByteLength: Int,
) {
    class IdentityModerationForbiddenException(
        message: String,
    ) : RuntimeException(message)

    class IdentityModerationConflictException(
        message: String,
    ) : RuntimeException(message)

    suspend fun block(
        requesterGid: String,
        targetGid: String,
    ) {
        ensureAdmin(requesterGid)
        val normalizedTargetGid = normalizeGid(targetGid)
        ensureUnprotectedTarget(requesterGid, normalizedTargetGid)
        val blockList = currentBlockList()
        if (normalizedTargetGid in blockList) {
            throw IdentityModerationConflictException("identity already blocked")
        }
        writeBlockList(blockList.blockedGids + normalizedTargetGid)
    }

    suspend fun unblock(
        requesterGid: String,
        targetGid: String,
    ) {
        ensureAdmin(requesterGid)
        val normalizedTargetGid = normalizeGid(targetGid)
        ensureUnprotectedTarget(requesterGid, normalizedTargetGid)
        val blockList = currentBlockList()
        if (normalizedTargetGid !in blockList) {
            throw IdentityModerationConflictException("identity is not blocked")
        }
        writeBlockList(blockList.blockedGids.filter { it != normalizedTargetGid })
    }

    suspend fun delete(
        requesterGid: String,
        targetGid: String,
    ): DeleteIdentityResult {
        ensureAdmin(requesterGid)
        val normalizedTargetGid = normalizeGid(targetGid)
        ensureUnprotectedTarget(requesterGid, normalizedTargetGid)
        return identityDeleter.deleteIdentity(normalizedTargetGid)
    }

    private suspend fun ensureAdmin(requesterGid: String) {
        val allowList = adminAllowList.get() ?: AdminAllowList(emptyList())
        if (requesterGid !in allowList) {
            throw IdentityModerationForbiddenException("not allowed to moderate identities")
        }
    }

    private suspend fun ensureUnprotectedTarget(
        requesterGid: String,
        targetGid: String,
    ) {
        if (requesterGid == targetGid) {
            throw IdentityModerationForbiddenException("not allowed to moderate this identity")
        }
        val allowList = adminAllowList.get() ?: AdminAllowList(emptyList())
        if (targetGid in allowList) {
            throw IdentityModerationForbiddenException("not allowed to moderate this identity")
        }
    }

    private fun normalizeGid(gid: String): String {
        val trimmed = gid.trim()
        if (trimmed.isEmpty()) {
            throw BadRequestException("gid must not be blank")
        }
        val decoded = runCatching { Base64.getUrlDecoder().decode(trimmed) }.getOrNull()
        if (decoded == null || decoded.size != expectedGidByteLength) {
            throw BadRequestException("gid has invalid format")
        }
        return trimmed
    }

    private suspend fun currentBlockList(): IdentityBlockList = identityBlockList.get() ?: IdentityBlockList(emptyList())

    private suspend fun writeBlockList(blockedGids: List<String>) {
        val content = blockedGids.distinct().sorted().toIdentityBlockListYaml()
        Files.writeString(identityBlockList.config.path, content)
        identityBlockList.get(forceRefresh = true)
    }
}

private fun List<String>.toIdentityBlockListYaml(): String =
    if (isEmpty()) {
        "blockedGids: []\n"
    } else {
        buildString {
            append("blockedGids:\n")
            this@toIdentityBlockListYaml.forEach { gid ->
                append("  - \"")
                append(gid)
                append("\"\n")
            }
        }
    }
