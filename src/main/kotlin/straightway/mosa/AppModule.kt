package straightway.mosa

import org.apache.lucene.store.MMapDirectory
import org.koin.dsl.module
import org.koin.dsl.onClose
import straightway.mosa.authorization.AuthorizationController
import straightway.mosa.authorization.PodCertificateService
import straightway.mosa.authorization.TransientEd25519Factory
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.nio.file.Paths
import java.security.MessageDigest

val appModule =
    module {
        single<EmbeddingService> { OnnxLocalEmbeddingService() } onClose { (it as? AutoCloseable)?.close() }
        single<VectorStore> {
            LuceneLocalVectorStore(
                directoryFactory = { MMapDirectory(Paths.get("mosa_lucene_index")) },
                maxResults = 1000,
                pageSize = 10,
                maxNotFinishedQueries = 10000,
                vectorSize = get<EmbeddingService>().dimension,
            )
        } onClose { (it as? AutoCloseable)?.close() }
        single<() -> MessageDigest> { { MessageDigest.getInstance("SHA-256") } }
        single<SignatureFactory> { TransientEd25519Factory() }

        single<ContentService> { ContentService(get(), get(), get()) }

        single<BaseController> { ContentController(get()) }
        single<PodCertificateService> { PodCertificateService(get()) }
        single<BaseController> { AuthorizationController(get()) }
    }
