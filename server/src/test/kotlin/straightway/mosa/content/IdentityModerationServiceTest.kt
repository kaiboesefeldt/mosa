package straightway.mosa.content

import io.ktor.server.plugins.BadRequestException
import kotlinx.coroutines.test.runTest
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.content.IdentityModerationService.IdentityModerationConflictException
import straightway.mosa.content.IdentityModerationService.IdentityModerationForbiddenException
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class IdentityModerationServiceTest {
    @Test
    fun `non admin cannot block identity`() =
        runTest {
            withFixture { fixture ->
                val requester = fixture.gid("requester")
                val target = fixture.gid("target")

                assertFailsWith<IdentityModerationForbiddenException> {
                    fixture.sut.block(requester, target)
                }
            }
        }

    @Test
    fun `admin cannot block own identity`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                fixture.writeAdminGids(admin)

                assertFailsWith<IdentityModerationForbiddenException> {
                    fixture.sut.block(admin, admin)
                }
            }
        }

    @Test
    fun `admin cannot block another admin identity`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                val targetAdmin = fixture.gid("target-admin")
                fixture.writeAdminGids(admin, targetAdmin)

                assertFailsWith<IdentityModerationForbiddenException> {
                    fixture.sut.block(admin, targetAdmin)
                }
            }
        }

    @Test
    fun `blocking already blocked identity returns conflict`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                val target = fixture.gid("target")
                fixture.writeAdminGids(admin)
                fixture.writeBlockedGids(target)

                assertFailsWith<IdentityModerationConflictException> {
                    fixture.sut.block(admin, target)
                }
            }
        }

    @Test
    fun `admin can unblock blocked identity`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                val target = fixture.gid("target")
                fixture.writeAdminGids(admin)
                fixture.writeBlockedGids(target)

                fixture.sut.unblock(admin, target)

                assertEquals(emptyList(), fixture.blockedGids())
            }
        }

    @Test
    fun `unblocking non blocked identity returns conflict`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                val target = fixture.gid("target")
                fixture.writeAdminGids(admin)

                assertFailsWith<IdentityModerationConflictException> {
                    fixture.sut.unblock(admin, target)
                }
            }
        }

    @Test
    fun `admin cannot delete own identity`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                fixture.writeAdminGids(admin)

                assertFailsWith<IdentityModerationForbiddenException> {
                    fixture.sut.delete(admin, admin)
                }
            }
        }

    @Test
    fun `admin cannot delete another admin identity`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                val targetAdmin = fixture.gid("target-admin")
                fixture.writeAdminGids(admin, targetAdmin)

                assertFailsWith<IdentityModerationForbiddenException> {
                    fixture.sut.delete(admin, targetAdmin)
                }
            }
        }

    @Test
    fun `invalid gid is rejected with bad request`() =
        runTest {
            withFixture { fixture ->
                val admin = fixture.gid("admin")
                fixture.writeAdminGids(admin)

                assertFailsWith<BadRequestException> {
                    fixture.sut.block(admin, "not-base64")
                }
            }
        }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val fixture = Fixture()
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private class Fixture {
        private val directory = Files.createTempDirectory("identity-moderation-service")
        private val adminPath = directory.resolve("mosa_admin_gids.yaml")
        private val clock = Clock.fixed(Instant.parse("2026-04-09T12:00:00Z"), ZoneId.of("UTC"))
        private val deletionUseCase = FakeIdentityDeleter()
        private val blockedIdentityStore = InMemoryBlockedIdentityStore()
        private val adminAllowList =
            AutoRefreshingFileBackedContent(
                AutoRefreshingFileBackedContent.Config(adminPath, 5.seconds),
                clock,
                transformer = AdminAllowList.transformer,
            )
        val sut =
            IdentityModerationService(
                adminAllowList = adminAllowList,
                blockedIdentityStore = blockedIdentityStore,
                identityDeleter = deletionUseCase,
                expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
            )

        suspend fun writeAdminGids(vararg gids: String) {
            Files.writeString(adminPath, gids.toList().toAdminAllowListYaml())
            adminAllowList.get(forceRefresh = true)
        }

        suspend fun writeBlockedGids(vararg gids: String) {
            gids.forEach { blockedIdentityStore.block(it) }
        }

        suspend fun blockedGids(): List<String> = blockedIdentityStore.blockedGids()

        fun gid(seed: String): String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
                MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()),
            )

        fun close() {
            Files.deleteIfExists(adminPath)
            Files.deleteIfExists(directory)
        }
    }

    private class FakeIdentityDeleter : IdentityDeleter {
        override fun deleteIdentity(gid: String): DeleteIdentityResult = DeleteIdentityResult(emptyList(), 0, 0)
    }

    private class InMemoryBlockedIdentityStore : BlockedIdentityStore {
        private val blockedGids = linkedSetOf<String>()

        override fun isBlocked(gid: String): Boolean = gid in blockedGids

        override fun block(gid: String): Boolean = blockedGids.add(gid)

        override fun unblock(gid: String): Boolean = blockedGids.remove(gid)

        override fun delete(gid: String): Boolean = blockedGids.remove(gid)

        fun blockedGids(): List<String> = blockedGids.toList()
    }
}

private fun List<String>.toAdminAllowListYaml(): String =
    if (isEmpty()) {
        "adminGids: []\n"
    } else {
        buildString {
            append("adminGids:\n")
            this@toAdminAllowListYaml.forEach { gid ->
                append("  - \"")
                append(gid)
                append("\"\n")
            }
        }
    }
