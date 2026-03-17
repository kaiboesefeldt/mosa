package straightway.mosa.message

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.testutils.setJsonBody
import java.nio.file.Files
import java.time.Instant
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class AdminMessageIntegrationTest {
    @Test
    fun `admin can send a message and recipient can fetch it`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("messages.json")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = FileBackedAdminMessageStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    idFactory = { "message-1" },
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            adminGid = adminIdentity.gid

            try {
                env {
                    val sendResponse =
                        client.post("/v1/admin/messages") {
                            bearerAuth(adminIdentity.token)
                            setJsonBody(CreateAdminMessageRequest(recipientIdentity.gid, "Subject", "Body"))
                        }

                    assertEquals(HttpStatusCode.NoContent, sendResponse.status)

                    val inboxResponse =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }

                    assertEquals(HttpStatusCode.OK, inboxResponse.status)
                    assertEquals(
                        AdminMessageListResult(
                            listOf(AdminMessage("message-1", adminIdentity.gid, 1_773_742_530, "Subject", "Body")),
                        ),
                        inboxResponse.body(),
                    )
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `other users cannot read recipient inbox`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("messages.json")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = FileBackedAdminMessageStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    idFactory = { "message-1" },
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            val otherIdentity = testIdentity(env)
            adminGid = adminIdentity.gid

            try {
                env {
                    client.post("/v1/admin/messages") {
                        bearerAuth(adminIdentity.token)
                        setJsonBody(CreateAdminMessageRequest(recipientIdentity.gid, "Subject", "Body"))
                    }

                    val otherInboxResponse =
                        client.get("/v1/messages") {
                            bearerAuth(otherIdentity.token)
                        }

                    assertEquals(HttpStatusCode.OK, otherInboxResponse.status)
                    assertEquals(AdminMessageListResult(emptyList()), otherInboxResponse.body())
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `non admins cannot send admin messages`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("messages.json")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = FileBackedAdminMessageStore(path)
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { false },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    idFactory = { "message-1" },
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))

            try {
                env {
                    val response =
                        client.post("/v1/admin/messages") {
                            bearerAuth(env.bearerToken)
                            setJsonBody(CreateAdminMessageRequest("recipient-gid", "Subject", "Body"))
                        }

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `unretrieved messages disappear after long ttl`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("messages.json")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = FileBackedAdminMessageStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    idFactory = { "message-1" },
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            adminGid = adminIdentity.gid

            try {
                env {
                    client.post("/v1/admin/messages") {
                        bearerAuth(adminIdentity.token)
                        setJsonBody(CreateAdminMessageRequest(recipientIdentity.gid, "Subject", "Body"))
                    }
                    clock.advanceSeconds(180L * 24 * 60 * 60 + 1)

                    val response =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }

                    assertEquals(AdminMessageListResult(emptyList()), response.body())
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `retrieved messages remain visible until short ttl expires`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("messages.json")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = FileBackedAdminMessageStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    idFactory = { "message-1" },
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            adminGid = adminIdentity.gid

            try {
                env {
                    client.post("/v1/admin/messages") {
                        bearerAuth(adminIdentity.token)
                        setJsonBody(CreateAdminMessageRequest(recipientIdentity.gid, "Subject", "Body"))
                    }

                    val firstFetch =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }
                    assertEquals(1, firstFetch.body<AdminMessageListResult>().entries.size)

                    clock.advanceSeconds(27L * 24 * 60 * 60)

                    val secondFetch =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }
                    assertEquals(1, secondFetch.body<AdminMessageListResult>().entries.size)

                    clock.advanceSeconds(24L * 60 * 60 + 1)

                    val expiredFetch =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }
                    assertEquals(AdminMessageListResult(emptyList()), expiredFetch.body())
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    private fun testIdentity(env: ControllerHttpTestEnvironment): TestIdentity {
        val keyPair = env.crypto.generateKeyPair()
        val gid =
            Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT)
                .encode(
                    env.hasherFactory().digest(
                        keyPair.public.encoded
                            .takeLast(32)
                            .toByteArray(),
                    ),
                )
        val certificate = env.podCertService.approveGid(gid, validitySeconds = 3600L)
        return TestIdentity(gid = gid, token = env.jwtBuilder.buildMisJwt(keyPair, certificate))
    }

    private data class TestIdentity(
        val gid: String,
        val token: String,
    )
}
