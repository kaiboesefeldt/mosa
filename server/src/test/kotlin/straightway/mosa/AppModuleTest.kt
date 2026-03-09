package straightway.mosa

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertDoesNotThrow
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.VectorStore.Transaction
import straightway.mosa.auth.Auth0TokenUtil
import straightway.mosa.auth.AuthenticationController
import straightway.mosa.auth.FileBackedEd25519Factory
import straightway.mosa.auth.PodCertificateService
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.content.PrivacySnapshotController
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.legal.LegalController
import straightway.mosa.legal.LegalService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.webclient.WebClientCommandLineOptions.Companion.SERVE_SOURCE_MAPS
import straightway.mosa.webclient.WebClientController
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class AppModuleTest {
    private val embedding = floatArrayOf(1.0f, 2.0f, 3.0f)
    private var koinApplication: KoinApplication? = null
    private val vectorStore = mockk<LuceneLocalVectorStore>()
    private var originalUserDir: String? = null
    private var testWorkingDir: Path? = null

    private val koin: Koin
        get() = checkNotNull(koinApplication).koin

    @BeforeEach
    fun setUp() {
        originalUserDir = System.getProperty("user.dir")
        testWorkingDir = Files.createTempDirectory("mosa-test-")
        System.setProperty("user.dir", testWorkingDir.toString())
        mockkObject(LuceneLocalVectorStore.Companion)
        mockOnnxEmbeddingService()
        mockVectorStore()
        koinApplication = createKoinApplication()
    }

    @AfterEach
    fun tearDown() {
        koinApplication?.close()
        koinApplication = null
        OnnxLocalEmbeddingService.embeddingServiceFactory = null
        unmockkObject(LuceneLocalVectorStore.Companion)
        testWorkingDir?.resolve("mosa_pod_keypair.pem")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_snapshot_archive_key.txt")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.let { Files.deleteIfExists(it) }
        originalUserDir?.let { System.setProperty("user.dir", it) }
    }

    @Test
    fun `provides embedding service singleton`() {
        assertIs<OnnxLocalEmbeddingService>(koin.get<EmbeddingService>())
    }

    @Test
    fun `provides clock singleton`() {
        assertIs<Clock>(koin.get<Clock>())
    }

    @Test
    fun `provides random singleton`() {
        assertSame(koin.get<Random>(), koin.get<Random>())
    }

    @Test
    fun `provides lucene vector store singleton`() {
        assertSame(koin.get<VectorStore>(), koin.get<VectorStore>())
        verify(exactly = 1) {
            LuceneLocalVectorStore.create(
                indexAccess = any(),
                paginationRegistry = any(),
                queryPlanner = any(),
                clock = koin.get<Clock>(),
                vectorSize = embedding.size,
            )
        }
    }

    @Test
    fun `provides sha256 hasher factory singleton`() {
        val hasherFactory = koin.get<HasherFactory>()
        val firstDigest = hasherFactory()
        val secondDigest = hasherFactory()
        assertEquals("SHA-256", firstDigest.algorithm)
        assertNotSame(firstDigest, secondDigest)
    }

    @Test
    fun `provides file backed ed25519 signature factory singleton`() {
        val signatureFactory = koin.get<SignatureFactory>()
        assertIs<FileBackedEd25519Factory>(signatureFactory)
    }

    @Test
    fun `provides ed25519 crypto singleton`() {
        assertIs<CryptoEd25519>(koin.get<Crypto>())
    }

    @Test
    fun `provides auth0 token util singleton`() {
        assertIs<Auth0TokenUtil>(koin.get<TokenUtil>())
    }

    @Test
    fun `provides content service singleton`() {
        assertDoesNotThrow { koin.get<ContentService>() }
    }

    @Test
    fun `provides pod certificate service singleton`() {
        assertDoesNotThrow { koin.get<PodCertificateService>() }
    }

    @Test
    fun `provides command line argument store singleton`() {
        koinApplication!!.close()
        val arguments =
            CommandLineArgumentStore.from { name ->
                if (name == SERVE_SOURCE_MAPS) true else null
            }
        koinApplication = createKoinApplication(arguments)
        assertSame(arguments, koin.get<CommandLineArgumentStore>())
    }

    @Test
    fun `loads controllers bound to base controller`() {
        val controllerTypes = koin.getAll<BaseController>().map { it::class }.toSet()
        assertEquals(
            setOf(
                ContentController::class,
                PrivacySnapshotController::class,
                AuthenticationController::class,
                LegalController::class,
                WebClientController::class,
            ),
            controllerTypes,
        )
        controllerTypes.forEach { controllerType ->
            assertDoesNotThrow { koin.get(controllerType) }
        }
    }

    @Test
    fun `WebClientController does not serve source maps by default`() {
        assertFalse(koin.get<WebClientController>().serveSourceMaps)
    }

    @Test
    fun `WebClientController serves source maps if switched on`() {
        koinApplication!!.close()
        koinApplication =
            createKoinApplication(
                CommandLineArgumentStore.from {
                    if (it == SERVE_SOURCE_MAPS) true else null
                },
            )
        assertTrue(koin.get<WebClientController>().serveSourceMaps)
    }

    @Test
    fun `closes resources when application stops`() {
        val autoCloseables =
            listOf(
                koin.get<VectorStore>(),
                koin.get<EmbeddingService>() as AutoCloseable,
            )

        koinApplication?.close()
        koinApplication = null

        autoCloseables.forEach { autoCloseable ->
            verify(exactly = 1) { autoCloseable.close() }
        }
    }

    private fun mockOnnxEmbeddingService() {
        OnnxLocalEmbeddingService.embeddingServiceFactory =
            mockk {
                every { create(any()) } returns
                    mockk(relaxed = true) {
                        every { dimension } returns embedding.size
                    }
            }
    }

    private fun mockVectorStore() {
        every {
            LuceneLocalVectorStore.create(
                indexAccess = any(),
                paginationRegistry = any(),
                queryPlanner = any(),
                clock = any(),
                vectorSize = embedding.size,
            )
        } returns vectorStore
        every { vectorStore.close() } just runs
        coEvery { vectorStore.writeAccess(any()) } answers {
            with(mockk<Transaction>(relaxed = true)) {
                arg<suspend Transaction.() -> Unit>(0)
            }
        }
    }

    private fun createKoinApplication(arguments: CommandLineArgumentStore = CommandLineArgumentStore.EMPTY): KoinApplication =
        koinApplication {
            allowOverride(true)
            modules(
                module {
                    configureMosaAppModule(arguments)
                    single<LegalService> { mockk(relaxed = true) }
                },
            )
        }
}
