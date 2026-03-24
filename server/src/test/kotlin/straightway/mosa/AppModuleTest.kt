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
import org.koin.core.error.InstanceCreationException
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
import straightway.mosa.legal.ImprintService
import straightway.mosa.legal.LegalController
import straightway.mosa.legal.LegalService
import straightway.mosa.message.AdminMessageController
import straightway.mosa.message.AdminMessageService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import straightway.mosa.webclient.WebClientCommandLineOptions.Companion.SERVE_SOURCE_MAPS
import straightway.mosa.webclient.WebClientController
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    private val moduleWorkingDir: Path = Path.of("").toAbsolutePath().normalize()

    private val koin: Koin
        get() = checkNotNull(koinApplication).koin

    @BeforeEach
    fun setUp() {
        originalUserDir = System.getProperty("user.dir")
        testWorkingDir = Files.createTempDirectory("mosa-test-")
        System.setProperty("user.dir", testWorkingDir.toString())
        writeLegalOperatorProfiles(checkNotNull(testWorkingDir))
        writeImprintConfig(checkNotNull(testWorkingDir))
        writeLegalOperatorProfiles(moduleWorkingDir)
        writeImprintConfig(moduleWorkingDir)
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
        testWorkingDir?.resolve("mosa_db.sqlite")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_pod_keypair.pem")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_snapshot_archive_keys.json")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_privacy.yaml")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_terms.yaml")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("mosa_imprint.yaml")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.resolve("imprint.yaml")?.let { Files.deleteIfExists(it) }
        testWorkingDir?.let { Files.deleteIfExists(it) }
        Files.deleteIfExists(moduleWorkingDir.resolve("mosa_privacy.yaml"))
        Files.deleteIfExists(moduleWorkingDir.resolve("mosa_terms.yaml"))
        Files.deleteIfExists(moduleWorkingDir.resolve("mosa_imprint.yaml"))
        Files.deleteIfExists(moduleWorkingDir.resolve("imprint.yaml"))
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
    fun `provides admin message service singleton`() {
        assertDoesNotThrow { koin.get<AdminMessageService>() }
    }

    @Test
    fun `provides pod certificate service singleton`() {
        assertDoesNotThrow { koin.get<PodCertificateService>() }
    }

    @Test
    fun `provides imprint service singleton`() {
        assertDoesNotThrow { koin.get<ImprintService>() }
    }

    @Test
    fun `requires mosa imprint config for startup`() {
        koinApplication?.close()
        koinApplication = null
        Files.deleteIfExists(moduleWorkingDir.resolve("mosa_imprint.yaml"))
        writeLegacyImprintConfig(moduleWorkingDir)

        val exception =
            assertFailsWith<InstanceCreationException> {
                createKoinApplication()
            }

        assertContains(exception.cause?.message.orEmpty(), "mosa_imprint.yaml")
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
                AdminMessageController::class,
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

    private fun writeLegalOperatorProfiles(directory: Path) {
        Files.writeString(directory.resolve("mosa_privacy.yaml"), privacyOperatorProfileYaml())
        Files.writeString(directory.resolve("mosa_terms.yaml"), termsOperatorProfileYaml())
    }

    private fun writeImprintConfig(directory: Path) {
        Files.writeString(
            directory.resolve("mosa_imprint.yaml"),
            """
            provider:
              name: "Example Pod GmbH"
            address:
              street: "Main Street 1"
              postal_code: "12345"
              city: "Berlin"
              country: "Deutschland"
            contact:
              email: "legal@example.invalid"
            """.trimIndent(),
        )
    }

    private fun writeLegacyImprintConfig(directory: Path) {
        Files.writeString(
            directory.resolve("imprint.yaml"),
            """
            provider:
              name: "Example Pod GmbH"
            address:
              street: "Main Street 1"
              postal_code: "12345"
              city: "Berlin"
              country: "Deutschland"
            contact:
              email: "legal@example.invalid"
            """.trimIndent(),
        )
    }

    private fun privacyOperatorProfileYaml(): String =
        """
        kind: profile
        id: privacy-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-privacy-gdpr-service
          version: "1.0.0-draft2"
        overrides:
          values:
            AUTHORITY_SUPERVISORY_ADDRESS: "Example Authority Street 2, 12345 Example City"
            AUTHORITY_SUPERVISORY_NAME: "Example Supervisory Authority"
            AUTHORITY_SUPERVISORY_WEBSITE: "https://authority.example"
            CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
            CONTACT_DPO_OR_NOT_REQUIRED: "not required"
            CONTACT_PRIVACY_EMAIL: "privacy@operator.example"
            DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
            ORG_CONTROLLER_ADDRESS: "Example Street 1, 12345 Example City"
            ORG_CONTROLLER_NAME: "Operator Corp"
            RECIPIENTS_PROCESSORS_INFO: "No processors"
            SERVICE_DISPLAY_NAME: "Operator Service"
            URL_CONTROLLER_WEBSITE: "https://operator.example"
        """.trimIndent()

    private fun termsOperatorProfileYaml(): String =
        """
        kind: profile
        id: terms-operator-service
        version: "1.0.0-draft2"
        parent:
          id: mosa-terms-service
          version: "1.0.0-draft1"
        overrides:
          values:
            ORG_CONTROLLER_NAME: "Operator Corp"
            CONTACT_CONTROLLER_EMAIL: "legal@operator.example"
            DOC_EFFECTIVE_DATE: "2026-03-13T00:00:00Z"
        """.trimIndent()

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
