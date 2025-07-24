package straightway.mosa

import org.apache.lucene.store.MMapDirectory
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
import straightway.mosa.auth.Auth0TokenUtil
import straightway.mosa.auth.AuthenticationController
import straightway.mosa.auth.PodCertificateService
import straightway.mosa.auth.TransientEd25519Factory
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.crypto.CryptoEd25519
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Clock

typealias HasherFactory = () -> MessageDigest

val appModule =
    module {
        single<EmbeddingService> { OnnxLocalEmbeddingService() } onClose { (it as? AutoCloseable)?.close() }
        single<Clock> { Clock.systemUTC() }
        single<VectorStore> {
            LuceneLocalVectorStore(
                directoryFactory = { MMapDirectory(Paths.get("mosa_lucene_index")) },
                clock = get<Clock>(),
                maxResults = 1000,
                pageSize = 10,
                maxNotFinishedQueries = 10000,
                vectorSize = get<EmbeddingService>().dimension,
            )
        } onClose { (it as? AutoCloseable)?.close() }
        single<HasherFactory> { { MessageDigest.getInstance("SHA-256") } }
        single<SignatureFactory> { TransientEd25519Factory() }
        single<Crypto> { CryptoEd25519() }
        single<TokenUtil> { Auth0TokenUtil(get(), get(), get(), get()) }

        single<ContentService> { ContentService(get(), get(), get()) }

        single<PodCertificateService> { PodCertificateService(get()) }

        single { ContentController(get()) } bind BaseController::class
        single { AuthenticationController(get()) } bind BaseController::class
    }
