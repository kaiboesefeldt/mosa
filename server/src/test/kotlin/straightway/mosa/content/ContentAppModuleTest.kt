package straightway.mosa.content

import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import straightway.mosa.CommandLineArgumentStore
import straightway.mosa.EmbeddingService
import straightway.mosa.HasherFactory
import straightway.mosa.VectorStore
import java.security.MessageDigest
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

    private fun dependenciesModule(vectorStore: VectorStore): Module =
        module {
            single<CommandLineArgumentStore> { CommandLineArgumentStore.from { null } }
            single<VectorStore> { vectorStore }
            single<EmbeddingService> { throw UnsupportedOperationException() }
            single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
            single<FreezeSnapshotArchiveKeyStore> {
                object : FreezeSnapshotArchiveKeyStore {
                    override fun findByKeyId(keyId: String): FreezeSnapshotArchiveKey? = null
                }
            }
        }
}
