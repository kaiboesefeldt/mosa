package straightway.mosa

import org.koin.dsl.module
import org.koin.dsl.onClose
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.embedding.OnnxLocalEmbeddingService
import straightway.mosa.vectorstore.DefaultVectorStore

val appModule =
    module {
        single<EmbeddingService> { OnnxLocalEmbeddingService() } onClose { (it as? AutoCloseable)?.close() }
        single<VectorStore> { DefaultVectorStore() } onClose { (it as? AutoCloseable)?.close() }
        single<ContentService> { ContentService(get()) }

        factory<BaseController> { ContentController(get()) }
    }
