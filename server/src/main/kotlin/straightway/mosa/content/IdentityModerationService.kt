package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.contains
import java.util.Base64

class IdentityModerationService(
    private val adminAllowList: AutoRefreshingFileBackedContent<AdminAllowList>,
    private val blockedIdentityStore: BlockedIdentityStore,
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
        if (!blockedIdentityStore.block(normalizedTargetGid)) {
            throw IdentityModerationConflictException("identity already blocked")
        }
    }

    suspend fun unblock(
        requesterGid: String,
        targetGid: String,
    ) {
        ensureAdmin(requesterGid)
        val normalizedTargetGid = normalizeGid(targetGid)
        ensureUnprotectedTarget(requesterGid, normalizedTargetGid)
        if (!blockedIdentityStore.unblock(normalizedTargetGid)) {
            throw IdentityModerationConflictException("identity is not blocked")
        }
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

    suspend fun status(
        requesterGid: String,
        targetGid: String,
    ): AdminIdentityModerationStatus {
        ensureAdmin(requesterGid)
        val normalizedTargetGid = normalizeGid(targetGid)
        return AdminIdentityModerationStatus(
            isAdmin = isAdmin(normalizedTargetGid),
            isBlocked = blockedIdentityStore.isBlocked(normalizedTargetGid),
        )
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

    private suspend fun isAdmin(gid: String): Boolean {
        val allowList = adminAllowList.get() ?: AdminAllowList(emptyList())
        return gid in allowList
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
}
