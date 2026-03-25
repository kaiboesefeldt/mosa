package straightway.mosa.message

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import straightway.mosa.content.ControllerHttpTestEnvironment
import straightway.mosa.content.StructuredStateDatabase
import straightway.mosa.testutils.setJsonBody
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class AdminMessageIntegrationTest {
    @Test
    fun `admin can send a message and recipient can fetch it`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
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
                            listOf(AdminMessage("message-1", adminIdentity.gid, 1_773_742_530, "Subject", "Body", 28)),
                            page = 1,
                            hasMore = false,
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
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
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
                    assertEquals(AdminMessageListResult(emptyList(), page = 1, hasMore = false), otherInboxResponse.body())
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
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { false },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
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
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
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

                    assertEquals(AdminMessageListResult(emptyList(), page = 1, hasMore = false), response.body())
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
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
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
                    assertEquals(AdminMessageListResult(emptyList(), page = 1, hasMore = false), expiredFetch.body())
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `incremental inbox refresh shortens ttl only for returned entries`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            var adminGid = ""
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it == adminGid },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            adminGid = adminIdentity.gid

            try {
                store.append(
                    AdminMessageRecord(
                        id = "older",
                        recipientGid = recipientIdentity.gid,
                        senderGid = adminIdentity.gid,
                        createdAt = 1_773_742_530,
                        subject = "Older",
                        body = "Body 1",
                        expiresAt = 1_789_294_530,
                    ),
                )
                store.append(
                    AdminMessageRecord(
                        id = "newer",
                        recipientGid = recipientIdentity.gid,
                        senderGid = adminIdentity.gid,
                        createdAt = 1_773_742_531,
                        subject = "Newer",
                        body = "Body 2",
                        expiresAt = 1_789_294_531,
                    ),
                )

                env {
                    val refreshResponse =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                            parameter("fromTime", "1773742531")
                        }

                    assertEquals(HttpStatusCode.OK, refreshResponse.status)
                    assertEquals(listOf("newer"), refreshResponse.body<AdminMessageListResult>().entries.map(AdminMessage::id))
                }

                clock.advanceSeconds(28L * 24 * 60 * 60 + 1)

                env {
                    val inboxResponse =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }

                    assertEquals(HttpStatusCode.OK, inboxResponse.status)
                    assertEquals(listOf("older"), inboxResponse.body<AdminMessageListResult>().entries.map(AdminMessage::id))
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `admin history is paged and does not shorten ttl`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            val adminGids = mutableSetOf<String>()
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { it in adminGids },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { error("not used") },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val adminIdentity = testIdentity(env)
            val secondAdminIdentity = testIdentity(env)
            val recipientIdentity = testIdentity(env)
            adminGids += adminIdentity.gid
            adminGids += secondAdminIdentity.gid

            try {
                repeat(11) { index ->
                    store.append(
                        AdminMessageRecord(
                            id = "message-${index + 1}",
                            recipientGid = recipientIdentity.gid,
                            senderGid = if (index % 2 == 0) adminIdentity.gid else secondAdminIdentity.gid,
                            createdAt = 1_773_742_530L + index,
                            subject = "Subject ${index + 1}",
                            body = "Body ${index + 1}",
                            expiresAt = 1_789_294_530,
                        ),
                    )
                }

                env {
                    val firstPage =
                        client.get("/v1/admin/messages") {
                            bearerAuth(adminIdentity.token)
                            parameter("gid", recipientIdentity.gid)
                        }
                    val secondPage =
                        client.get("/v1/admin/messages") {
                            bearerAuth(adminIdentity.token)
                            parameter("gid", recipientIdentity.gid)
                            parameter("page", "2")
                        }

                    assertEquals(HttpStatusCode.OK, firstPage.status)
                    assertEquals(HttpStatusCode.OK, secondPage.status)
                    assertEquals(1, firstPage.body<AdminMessageListResult>().page)
                    assertEquals(true, firstPage.body<AdminMessageListResult>().hasMore)
                    assertEquals(10, firstPage.body<AdminMessageListResult>().entries.size)
                    assertEquals(listOf("message-1"), secondPage.body<AdminMessageListResult>().entries.map(AdminMessage::id))
                }

                clock.advanceSeconds(28L * 24 * 60 * 60)

                env {
                    val inboxResponse =
                        client.get("/v1/messages") {
                            bearerAuth(recipientIdentity.token)
                        }

                    assertEquals(10, inboxResponse.body<AdminMessageListResult>().entries.size)
                    assertEquals(true, inboxResponse.body<AdminMessageListResult>().hasMore)
                }
            } finally {
                Files.deleteIfExists(path)
                Files.deleteIfExists(directory)
            }
        }

    @Test
    fun `non admins cannot read admin history`() =
        runTest {
            val directory = Files.createTempDirectory("admin-message-integration")
            val path = directory.resolve("mosa_db.sqlite")
            val clock = MutableClock(Instant.parse("2026-03-17T10:15:30Z"))
            val store = createStore(path)
            val service =
                AdminMessageService(
                    store = store,
                    clock = clock,
                    isAdmin = { false },
                    unretrievedTtl = 180.days,
                    retrievedTtl = 28.days,
                    pageSize = 10,
                    idFactory = { "message-1" },
                    expectedGidByteLength = MessageDigest.getInstance("SHA-256").digest(byteArrayOf()).size,
                )
            val env = ControllerHttpTestEnvironment(AdminMessageController(service))
            val identity = testIdentity(env)

            try {
                env {
                    val response =
                        client.get("/v1/admin/messages") {
                            bearerAuth(identity.token)
                            parameter("gid", "recipient-gid")
                        }

                    assertEquals(HttpStatusCode.Forbidden, response.status)
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

    private fun createStore(path: java.nio.file.Path): AdminMessageStore {
        val database = StructuredStateDatabase(path)
        database.initialize(AdminMessagesTable)
        return SqliteAdminMessageStore(database)
    }
}
