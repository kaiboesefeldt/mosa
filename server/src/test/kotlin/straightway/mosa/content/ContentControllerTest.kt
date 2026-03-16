package straightway.mosa.content

import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.authentication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import straightway.mosa.auth.PodPrincipal
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.crypto.toBase64
import straightway.mosa.testutils.setJsonBody
import java.security.KeyPair
import java.security.Security
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals

class ContentControllerTest {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val test get() =
        object {
            val contentService =
                mockk<ContentService>()
            val verifier =
                ContentSignatureVerifier(
                    CryptoEd25519(),
                    { java.security.MessageDigest.getInstance("SHA-256") },
                    { Signature.getInstance("Ed25519") },
                )
            val env =
                ControllerHttpTestEnvironment(
                    ContentController(
                        contentService,
                        verifier,
                        Clock.fixed(
                            Instant.ofEpochSecond(170000000L),
                            ZoneId.of("UTC"),
                        ),
                    ),
                ) {
                    single<ContentService> { contentService }
                }
        }

    @Nested
    inner class PublishEndpoint : TestEndpoint("/v1/publish") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) {
                        it.contentService.publish(any(), any(), any(), any())
                    }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                env {
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                            signature = signPost(env.clientKeyPair, "Text to publish"),
                        )
                    coEvery {
                        contentService.publish(env.gid, request, any(), env.clientKeyPair.public.toBase64())
                    } returns ""

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) {
                        contentService.publish(env.gid, request, any(), env.clientKeyPair.public.toBase64())
                    }
                }
            }

        @Test
        fun `should return 401 Unauthorized when principal disappears`() =
            with(test) {
                env {
                    application {
                        install(
                            createApplicationPlugin("DropPrincipal") {
                                onCall { call ->
                                    call.authentication.principal(
                                        Any(),
                                    )
                                }
                            },
                        )
                    }

                    val request =
                        PublishRequest(
                            text = "Text to publish",
                            signature = signPost(env.clientKeyPair, "Text to publish"),
                        )

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any(), any())
                    }
                }
            }

        @Test
        fun `should return 422 when signature is cryptographically invalid`() =
            with(test) {
                env {
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                            signature = signPost(env.clientKeyPair, "Different text"),
                        )

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any(), any())
                    }
                }
            }

        @Test
        fun `should return 422 when signature version is unsupported`() =
            with(test) {
                env {
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                            signature = signPost(env.clientKeyPair, "Text to publish"),
                            signatureVersion = 2,
                        )

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any(), any())
                    }
                }
            }

        @Test
        fun `should return 422 when principal gid does not match public key`() =
            with(test) {
                env {
                    application {
                        install(
                            createApplicationPlugin("SwapPrincipal") {
                                onCall { call ->
                                    call.authentication.principal(
                                        PodPrincipal(
                                            certificate = env.podCertificate.copy(gid = "other-gid"),
                                            publicKeyBase64 = env.clientKeyPair.public.toBase64(),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    val request =
                        PublishRequest(
                            text = "Text to publish",
                            signature = signPost(env.clientKeyPair, "Text to publish"),
                        )

                    val response =
                        client.post("/v1/publish") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                    coVerify(exactly = 0) {
                        contentService.publish(any(), any(), any(), any())
                    }
                }
            }
    }

    @Nested
    inner class ReplyEndpoint : TestEndpoint("/v1/reply") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.reply(any(), any(), any(), any()) }
                }
            }
        }

        @Test
        fun `should return 201 Created on success`() =
            with(test) {
                env {
                    val request =
                        ReplyRequest(
                            content =
                                PublishRequest(
                                    text = "Text to publish",
                                    signature = signReply(env.clientKeyPair, "Text to publish", "01234567"),
                                ),
                            rootId = "01234567",
                        )
                    coEvery {
                        contentService.reply(env.gid, request, any(), env.clientKeyPair.public.toBase64())
                    } returns ""

                    val response =
                        client.post("/v1/reply") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Created, response.status)
                    coVerify(exactly = 1) {
                        contentService.reply(env.gid, request, any(), env.clientKeyPair.public.toBase64())
                    }
                }
            }

        @Test
        fun `should return 422 when reply signature uses different root id`() =
            with(test) {
                env {
                    val request =
                        ReplyRequest(
                            content =
                                PublishRequest(
                                    text = "Text to publish",
                                    signature = signReply(env.clientKeyPair, "Text to publish", "other-root"),
                                ),
                            rootId = "01234567",
                        )

                    val response =
                        client.post("/v1/reply") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
                    coVerify(exactly = 0) { contentService.reply(any(), any(), any(), any()) }
                }
            }
    }

    @Nested
    inner class DeleteEndpoint {
        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val deletedIds = listOf("post-1", "reply-1")
                    coEvery { contentService.delete(env.gid, "post-1") } returns deletedIds

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(DeletePostsResult(deletedIds), response.body())
                    coVerify(exactly = 1) { contentService.delete(env.gid, "post-1") }
                }
            }

        @Test
        fun `should return 404 Not Found when post is missing`() =
            with(test) {
                env {
                    coEvery { contentService.delete(env.gid, "post-1") } throws
                        ContentService.ContentNotFoundException()

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.NotFound, response.status)
                }
            }

        @Test
        fun `should return 403 Forbidden when post belongs to another user`() =
            with(test) {
                env {
                    coEvery { contentService.delete(env.gid, "post-1") } throws
                        ContentService.ContentForbiddenException()

                    val response =
                        client.delete("/v1/posts/post-1") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.Forbidden, response.status)
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                env {
                    val response = client.delete("/v1/posts/post-1")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.delete(any(), any()) }
                }
            }
    }

    @Nested
    inner class CapabilitiesEndpoint {
        @Test
        fun `should return content capabilities for authenticated user`() =
            with(test) {
                env {
                    val expected = ContentCapabilitiesResult(isAdmin = true)
                    coEvery { contentService.contentCapabilities(env.gid) } returns expected

                    val response =
                        client.get("/v1/posts/capabilities") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(expected, response.body())
                    coVerify(exactly = 1) { contentService.contentCapabilities(env.gid) }
                }
            }

        @Test
        fun `should return 401 Unauthorized when not authenticated`() =
            with(test) {
                env {
                    val response = client.get("/v1/posts/capabilities")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.contentCapabilities(any()) }
                }
            }

        @Test
        fun `should return content capabilities for requested gid`() =
            with(test) {
                env {
                    val expected = ContentCapabilitiesResult(isAdmin = true)
                    coEvery { contentService.contentCapabilities(env.gid, "gid-target") } returns expected

                    val response =
                        client.get("/v1/posts/capabilities?gid=gid-target") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(expected, response.body())
                    coVerify(exactly = 1) { contentService.contentCapabilities(env.gid, "gid-target") }
                }
            }
    }

    @Nested
    inner class DeleteAllEndpoint {
        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val deletedIds = listOf("post-1", "post-2")
                    coEvery { contentService.deleteAllForUser(env.gid) } returns deletedIds

                    val response =
                        client.delete("/v1/posts") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(DeletePostsResult(deletedIds), response.body())
                    coVerify(exactly = 1) { contentService.deleteAllForUser(env.gid) }
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                env {
                    val response = client.delete("/v1/posts")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.deleteAllForUser(any()) }
                }
            }
    }

    @Nested
    inner class ExportCsvEndpoint {
        @Test
        fun `should return 200 OK with csv attachment on success`() =
            with(test) {
                env {
                    val csvBody =
                        """
                        id,type,root_id,stored_at_epoch,stored_at_iso8601,author_gid,text
                        entry-1,post,,100,1970-01-01T00:01:40Z,${env.gid},"Hello"
                        """.trimIndent()
                    coEvery { contentService.exportCsvForUser(env.gid) } returns csvBody

                    val response =
                        client.get("/v1/posts/export.csv") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals("text/csv; charset=utf-8", response.headers["Content-Type"])
                    assertEquals(
                        ContentDisposition.Attachment
                            .withParameter(
                                ContentDisposition.Parameters.FileName,
                                "mosa-export-1975-05-22.csv",
                            ).toString(),
                        response.headers["Content-Disposition"],
                    )
                    assertEquals(csvBody, response.body<String>())
                    coVerify(exactly = 1) { contentService.exportCsvForUser(env.gid) }
                }
            }

        @Test
        fun `should return 401 Unauthorized when no bearer token is specified`() =
            with(test) {
                env {
                    val response = client.get("/v1/posts/export.csv")

                    assertEquals(HttpStatusCode.Unauthorized, response.status)
                    coVerify(exactly = 0) { contentService.exportCsvForUser(any()) }
                }
            }

        @Test
        fun `should return 500 InternalServerError when export fails unexpectedly`() =
            with(test) {
                env {
                    coEvery { contentService.exportCsvForUser(env.gid) } throws IllegalStateException("boom")

                    val response =
                        client.get("/v1/posts/export.csv") {
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.InternalServerError, response.status)
                    coVerify(exactly = 1) { contentService.exportCsvForUser(env.gid) }
                }
            }
    }

    @Nested
    inner class QueryEndpoint : TestEndpoint("/v1/query") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.query(any<QueryRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryRequest(
                            topic = "Hello World",
                            fromTime = 1L,
                            threadRootId = "12345",
                        )
                    coEvery { contentService.query(any<QueryRequest>()) } returns queryResult

                    val response =
                        client.post("/v1/query") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.query(request) }
                    assertEquals(queryResult, response.body<QueryResult>())
                }
            }
    }

    @Nested
    inner class QueryOnEndpoint : TestEndpoint("/v1/queryon") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.queryOn(any<QueryOnRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryOnRequest(queryOnId = "queryOnId")
                    coEvery { contentService.queryOn(any<QueryOnRequest>()) } returns queryResult

                    val response =
                        client.post("/v1/queryon") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.queryOn(request) }
                    assertEquals(queryResult, response.body<QueryResult>())
                }
            }
    }

    @Nested
    inner class SearchEndpoint : TestEndpoint("/v1/search") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.search(any<QueryRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryRequest(
                            topic = "Hello World",
                            fromTime = 1L,
                            threadRootId = "12345",
                        )
                    coEvery { contentService.search(any<QueryRequest>()) } returns searchResult

                    val response =
                        client.post("/v1/search") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.search(request) }
                    assertEquals(searchResult, response.body<SearchResult>())
                }
            }
    }

    @Nested
    inner class SearchOnEndpoint : TestEndpoint("/v1/searchon") {
        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.searchOn(any<QueryOnRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        QueryOnRequest(queryOnId = "queryOnId")
                    coEvery { contentService.searchOn(any<QueryOnRequest>()) } returns searchResult

                    val response =
                        client.post("/v1/searchon") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.searchOn(request) }
                    assertEquals(searchResult, response.body<SearchResult>())
                }
            }
    }

    @Nested
    inner class FetchEndpoint : TestEndpoint("/v1/fetch") {
        private val fetchResult =
            FetchResult(
                entries =
                    listOf(
                        FetchResult.Entry(
                            id = "id1",
                            content = "content1",
                            authorGID = "authorGID1",
                            storedAt = 12345,
                            rootId = "rootId1",
                        ),
                        FetchResult.Entry(
                            id = "id2",
                            content = "content2",
                            authorGID = "authorGID2",
                            storedAt = 23456,
                            rootId = null,
                        ),
                    ),
            )

        override fun setup() {
            test.also {
                env = it.env
                serviceVerification = {
                    coVerify(exactly = 0) { it.contentService.fetch(any<FetchRequest>()) }
                }
            }
        }

        @Test
        fun `should return 200 OK on success`() =
            with(test) {
                env {
                    val request =
                        FetchRequest(fetchResult.entries.map { it.id })
                    coEvery { contentService.fetch(any<FetchRequest>()) } returns fetchResult

                    val response =
                        client.post("/v1/fetch") {
                            setJsonBody(request)
                            bearerAuth(env.bearerToken)
                        }

                    assertEquals(HttpStatusCode.OK, response.status)
                    coVerify(exactly = 1) { contentService.fetch(request) }
                    assertEquals(fetchResult, response.body<FetchResult>())
                }
            }
    }

    private companion object {
        private val queryResult =
            QueryResult(
                queryOnId = "queryOnId",
                entries =
                    listOf(
                        QueryResult.Entry(
                            id = "id",
                            content = "content",
                            similarity = 0.9f,
                            authorGID = "authorGID",
                            storedAt = 170000000L,
                            rootId = "rootId",
                        ),
                    ),
            )
        private val searchResult =
            SearchResult(
                queryOnId = "queryOnId",
                entries =
                    listOf(
                        SearchResult.Entry(
                            id = "id",
                            similarity = 0.9f,
                        ),
                    ),
            )
    }

    private fun signPost(
        keyPair: KeyPair,
        text: String,
    ): String = sign(keyPair, ContentSignaturePayload.forPost(text).toCanonicalBytes())

    private fun signReply(
        keyPair: KeyPair,
        text: String,
        rootId: String,
    ): String = sign(keyPair, ContentSignaturePayload.forReply(text, rootId).toCanonicalBytes())

    private fun sign(
        keyPair: KeyPair,
        payload: ByteArray,
    ): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(
            Signature.getInstance("Ed25519").run {
                initSign(keyPair.private)
                update(payload)
                sign()
            },
        )
}
