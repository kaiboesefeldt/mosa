package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.config.IdentityBlockList
import straightway.mosa.testutils.setJsonBody
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class IdentityModerationControllerTest {
    @Test
    fun `block endpoint returns no content and persists gid`() =
        runTest {
            withFixture { fixture ->
                fixture.writeAdminGids(fixture.environment.gid)
                val targetGid = fixture.gid("target")

                fixture.environment {
                    val response =
                        client.post("/v1/admin/identities/block") {
                            bearerAuth(fixture.environment.bearerToken)
                            setJsonBody(IdentityModerationRequest(targetGid))
                        }

                    assertEquals(HttpStatusCode.NoContent, response.status)
                    assertEquals(listOf(targetGid), fixture.blockedGids())
                }
            }
        }

    @Test
    fun `unblock endpoint returns no content and removes gid`() =
        runTest {
            withFixture { fixture ->
                fixture.writeAdminGids(fixture.environment.gid)
                val targetGid = fixture.gid("target")
                fixture.writeBlockedGids(targetGid)

                fixture.environment {
                    val response =
                        client.post("/v1/admin/identities/unblock") {
                            bearerAuth(fixture.environment.bearerToken)
                            setJsonBody(IdentityModerationRequest(targetGid))
                        }

                    assertEquals(HttpStatusCode.NoContent, response.status)
                    assertEquals(emptyList(), fixture.blockedGids())
                }
            }
        }

    @Test
    fun `delete endpoint returns deletion result for target gid`() =
        runTest {
            withFixture { fixture ->
                fixture.writeAdminGids(fixture.environment.gid)
                val targetGid = fixture.gid("target")
                val expected = DeleteIdentityResult(listOf("post-1"), 2, 3)
                fixture.deletionUseCase.result = expected

                fixture.environment {
                    val response =
                        client.delete("/v1/admin/identities/$targetGid") {
                            bearerAuth(fixture.environment.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(expected, response.body())
                    assertEquals(targetGid, fixture.deletionUseCase.deletedGids.single())
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
        private val directory = Files.createTempDirectory("identity-moderation-controller")
        private val adminPath = directory.resolve("mosa_admin_gids.yaml")
        private val blockPath = directory.resolve("mosa_identity_blocklist.yaml")
        private val clock = Clock.fixed(Instant.parse("2026-04-09T12:00:00Z"), ZoneId.of("UTC"))
        val deletionUseCase = RecordingIdentityDeleter()
        private val adminAllowList =
            AutoRefreshingFileBackedContent(
                AutoRefreshingFileBackedContent.Config(adminPath, 5.seconds),
                clock,
                transformer = AdminAllowList.transformer,
            )
        private val identityBlockList =
            AutoRefreshingFileBackedContent(
                AutoRefreshingFileBackedContent.Config(blockPath, 5.seconds),
                clock,
                transformer = IdentityBlockList.transformer,
            )
        private val service =
            IdentityModerationService(
                adminAllowList = adminAllowList,
                identityBlockList = identityBlockList,
                identityDeleter = deletionUseCase,
                expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
            )
        val environment = ControllerHttpTestEnvironment(IdentityModerationController(service))

        suspend fun writeAdminGids(vararg gids: String) {
            Files.writeString(adminPath, gids.toList().toAdminAllowListYaml())
            adminAllowList.get(forceRefresh = true)
        }

        suspend fun writeBlockedGids(vararg gids: String) {
            Files.writeString(blockPath, gids.toList().toIdentityBlockListYaml())
            identityBlockList.get(forceRefresh = true)
        }

        suspend fun blockedGids(): List<String> = (identityBlockList.get(forceRefresh = true) ?: IdentityBlockList(emptyList())).blockedGids

        fun gid(seed: String): String =
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
                MessageDigest.getInstance("SHA-256").digest(seed.toByteArray()),
            )

        fun close() {
            Files.deleteIfExists(adminPath)
            Files.deleteIfExists(blockPath)
            Files.deleteIfExists(directory)
        }
    }

    private class RecordingIdentityDeleter : IdentityDeleter {
        val deletedGids = mutableListOf<String>()
        var result = DeleteIdentityResult(emptyList(), 0, 0)

        override fun deleteIdentity(gid: String): DeleteIdentityResult {
            deletedGids += gid
            return result
        }
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
