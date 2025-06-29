package straightway.mosa

import org.apache.lucene.store.MMapDirectory
import org.koin.dsl.module
import org.koin.dsl.onClose
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.LuceneLocalVectorStore
import java.nio.file.Paths

val appModule =
    module {
        single<EmbeddingService> { OnnxLocalEmbeddingService() } onClose { (it as? AutoCloseable)?.close() }

        single<VectorStore> {
            LuceneLocalVectorStore(
                directoryFactory = { MMapDirectory(Paths.get("mosa_lucene_index")) },
                maxResults = 1000,
                pageSize = 10,
                maxNotFinishedSearches = 10000,
                vectorSize = get<EmbeddingService>().dimension,
            )
        } onClose { (it as? AutoCloseable)?.close() }

        single<ContentService> { ContentService(get()) }

        single<BaseController> { ContentController(get()) }
    }
