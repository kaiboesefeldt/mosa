package straightway.mosa

import org.koin.dsl.module
import straightway.mosa.base.BaseController
import straightway.mosa.content.ContentController
import straightway.mosa.content.ContentService
import straightway.mosa.embedding.DefaultEmbeddingService
import straightway.mosa.vectorstore.DefaultVectorStore

val appModule =
    module {
        single<EmbeddingService> { DefaultEmbeddingService() }
        single<VectorStore> { DefaultVectorStore() }
        single<ContentService> { ContentService() }

        factory<BaseController> { ContentController(get()) }
    }
