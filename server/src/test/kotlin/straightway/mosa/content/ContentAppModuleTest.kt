package straightway.mosa.content

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.Crypto
import straightway.mosa.EmbeddingService
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.SignatureVerifierProvider
import straightway.mosa.VectorStore
import straightway.mosa.config.AdminAllowList
import straightway.mosa.config.AutoRefreshingFileBackedContent
import straightway.mosa.content.snapshot.FreezeSnapshotCryptoService
import straightway.mosa.content.snapshot.FreezeSnapshotFormat
import straightway.mosa.content.snapshot.FreezeSnapshotRestoreService
import straightway.mosa.content.snapshot.FreezeSnapshotRestoreValidator
import straightway.mosa.content.snapshot.FreezeSnapshotSelectionService
import straightway.mosa.content.snapshot.FreezeSnapshotService
import straightway.mosa.content.snapshot.FreezeSnapshotSizePolicy
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.defense.adminAllowList
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.time.Clock
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class ContentAppModuleTest {
    @Test
    fun `provides freeze snapshot selection service as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotSelectionService>()
            val second = koinApplication.koin.get<FreezeSnapshotSelectionService>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot format as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotFormat>()
            val second = koinApplication.koin.get<FreezeSnapshotFormat>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot crypto service as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotCryptoService>()
            val second = koinApplication.koin.get<FreezeSnapshotCryptoService>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot service as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotService>()
            val second = koinApplication.koin.get<FreezeSnapshotService>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot restore service as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotRestoreService>()
            val second = koinApplication.koin.get<FreezeSnapshotRestoreService>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot restore validator as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotRestoreValidator>()
            val second = koinApplication.koin.get<FreezeSnapshotRestoreValidator>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides freeze snapshot size policy as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<FreezeSnapshotSizePolicy>()
            val second = koinApplication.koin.get<FreezeSnapshotSizePolicy>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    @Test
    fun `provides content signature verifier as singleton`() {
        val vectorStore = mockk<VectorStore>()
        val koinApplication =
            koinApplication {
                modules(
                    dependenciesModule(
                        vectorStore = vectorStore,
                    ),
                    module {
                        configureContentAppModule()
                    },
                )
            }

        try {
            val first = koinApplication.koin.get<ContentSignatureVerifier>()
            val second = koinApplication.koin.get<ContentSignatureVerifier>()

            assertSame(first, second)
        } finally {
            koinApplication.close()
        }
    }

    private fun dependenciesModule(vectorStore: VectorStore): Module =
        module {
            val publicKey = mockk<PublicKey>()
            val embeddingService = mockk<EmbeddingService>()
            val signatureFactory = mockk<SignatureFactory>()
            every { publicKey.encoded } returns byteArrayOf(1, 2, 3)
            every { signatureFactory.publicKey } returns publicKey
            single<CommandLineArgumentStore> { CommandLineArgumentStore.from { null } }
            single { Clock.systemUTC() }
            single<VectorStore> { vectorStore }
            single<EmbeddingService> { embeddingService }
            single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
            single<SignatureFactory> { signatureFactory }
            single<Crypto> { CryptoEd25519() }
            single<SignatureVerifierProvider> { SignatureVerifierProvider { Signature.getInstance("Ed25519") } }
            single(adminAllowList) {
                AutoRefreshingFileBackedContent(
                    config =
                        AutoRefreshingFileBackedContent.Config(
                            path = Path.of("missing-admin-allow-list.yaml"),
                            refreshInterval = 1.seconds,
                        ),
                    clock = get(),
                    transformer =
                        AutoRefreshingFileBackedContent.Transformer {
                            AdminAllowList(emptyList())
                        },
                )
            }
        }
}
