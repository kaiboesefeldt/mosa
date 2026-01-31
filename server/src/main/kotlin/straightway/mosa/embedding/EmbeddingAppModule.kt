package straightway.mosa.embedding

import org.koin.core.module.Module
import org.koin.dsl.onClose
import straightway.mosa.EmbeddingService

fun Module.configureEmbeddingAppModule() {
    single<EmbeddingService> { OnnxLocalEmbeddingService.create() } onClose { (it as? AutoCloseable)?.close() }
}
