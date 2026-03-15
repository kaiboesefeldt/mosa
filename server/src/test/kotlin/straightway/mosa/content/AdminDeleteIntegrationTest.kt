package straightway.mosa.content

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.apache.lucene.store.ByteBuffersDirectory
import straightway.mosa.EmbeddingService
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.vectorstore.LuceneIndexAccess
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.vectorstore.LuceneQueryPlanner
import straightway.mosa.vectorstore.QueryOnRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class AdminDeleteIntegrationTest {
    @Test
    fun `delete endpoint allows deleting foreign post after admin list reload`() {
        val adminPath = Path.of("mosa_admin_gids.yaml")
        Files.deleteIfExists(adminPath)
        val adminClock = MutableClock(Instant.EPOCH)
        val embeddingService = mockk<EmbeddingService>()
        every { embeddingService.dimension } returns 3
        coEvery { embeddingService.embed(any()) } returns floatArrayOf(0.1f, 0.2f, 0.3f)
        val adminAllowList =
            AutoRefreshingFileBackedContent(
                config = AutoRefreshingFileBackedContent.Config(adminPath, 5.seconds),
                clock = adminClock,
                transformer = AdminAllowList.transformer,
            )
        val vectorStore =
            LuceneLocalVectorStore.create(
                indexAccess = LuceneIndexAccess { ByteBuffersDirectory() },
                paginationRegistry = QueryOnRegistry(pageSize = 10, maxNotFinishedQueries = 10000),
                queryPlanner = LuceneQueryPlanner(maxResults = 1000),
                clock =
                    Clock.fixed(
                        Instant.ofEpochSecond(170000000L),
                        ZoneId.of("UTC"),
                    ),
                vectorSize = embeddingService.dimension,
            )
        val contentService =
            ContentService(
                embeddingService = embeddingService,
                vectorStore = vectorStore,
                hasherFactory = { MessageDigest.getInstance("SHA-256") },
                maxFetchItems = 100,
                isAdmin = { gid -> (adminAllowList.get() ?: AdminAllowList(emptyList())).adminGids.contains(gid) },
            )
        val environment =
            ControllerHttpTestEnvironment(
                ContentController(
                    contentService = contentService,
                    contentSignatureVerifier =
                        ContentSignatureVerifier(
                            CryptoEd25519(),
                            { MessageDigest.getInstance("SHA-256") },
                            { Signature.getInstance("Ed25519") },
                        ),
                    clock =
                        Clock.fixed(
                            Instant.ofEpochSecond(170000000L),
                            ZoneId.of("UTC"),
                        ),
                ),
            )

        try {
            val postId = runBlocking { contentService.publish("other-user", PublishRequest("foreign post"), 1L, "public-key") }

            environment {
                val forbidden =
                    client.delete("/v1/posts/$postId") {
                        bearerAuth(environment.bearerToken)
                    }

                assertEquals(HttpStatusCode.Forbidden, forbidden.status)

                adminClock.advanceSeconds(10)
                Files.writeString(adminPath, "adminGids:\n  - \"${environment.gid}\"\n")

                val allowed =
                    client.delete("/v1/posts/$postId") {
                        bearerAuth(environment.bearerToken)
                    }

                assertEquals(HttpStatusCode.OK, allowed.status)
            }
        } finally {
            vectorStore.close()
            Files.deleteIfExists(adminPath)
        }
    }

    private class MutableClock(
        private var instant: Instant,
        private val zoneId: ZoneId = ZoneId.systemDefault(),
    ) : Clock() {
        override fun getZone(): ZoneId = zoneId

        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)

        override fun instant(): Instant = instant

        fun advanceSeconds(seconds: Long) {
            instant = instant.plus(seconds, ChronoUnit.SECONDS)
        }
    }
}
