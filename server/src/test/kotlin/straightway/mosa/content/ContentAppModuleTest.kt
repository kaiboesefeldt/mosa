package straightway.mosa.content

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.EmbeddingService
import straightway.mosa.HasherFactory
import straightway.mosa.SignatureFactory
import straightway.mosa.VectorStore
import straightway.mosa.content.snapshot.FreezeSnapshotActiveArchiveKeyProvider
import straightway.mosa.content.snapshot.FreezeSnapshotArchiveKey
import straightway.mosa.content.snapshot.FreezeSnapshotArchiveKeyStore
import straightway.mosa.content.snapshot.FreezeSnapshotCryptoService
import straightway.mosa.content.snapshot.FreezeSnapshotFormat
import straightway.mosa.content.snapshot.FreezeSnapshotSelectionService
import straightway.mosa.content.snapshot.FreezeSnapshotService
import java.security.MessageDigest
import java.security.PublicKey
import java.time.Clock
import kotlin.test.assertSame

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

    private fun dependenciesModule(vectorStore: VectorStore): Module =
        module {
            val publicKey = mockk<PublicKey>()
            val signatureFactory = mockk<SignatureFactory>()
            every { publicKey.encoded } returns byteArrayOf(1, 2, 3)
            every { signatureFactory.publicKey } returns publicKey
            single<CommandLineArgumentStore> { CommandLineArgumentStore.from { null } }
            single { Clock.systemUTC() }
            single<VectorStore> { vectorStore }
            single<EmbeddingService> { throw UnsupportedOperationException() }
            single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
            single<SignatureFactory> { signatureFactory }
            single<FreezeSnapshotArchiveKeyStore> {
                object : FreezeSnapshotArchiveKeyStore {
                    override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = null
                }
            }
            single<FreezeSnapshotActiveArchiveKeyProvider> {
                FreezeSnapshotActiveArchiveKeyProvider {
                    FreezeSnapshotArchiveKey("archive-2026", ByteArray(16) { (it + 1).toByte() })
                }
            }
        }
}
