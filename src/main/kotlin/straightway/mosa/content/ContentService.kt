package straightway.mosa.content

import io.github.oshai.kotlinlogging.KotlinLogging
import straightway.mosa.EmbeddingService
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ContentService(
    private val embeddingService: EmbeddingService,
) {
    suspend fun publish(request: PublishRequest) {
        log.debug { request }
        val embeddingVector = embeddingService.embed(request.text)
        log.debug { "Embedding Vector for '${request.text}': ${embeddingVector.toList().take(10)}..." }
    }

    suspend fun reply(request: ReplyRequest) {
        log.debug { request }
    }

    suspend fun query(request: QueryRequest): QueryResult {
        log.debug { request }
        return QueryResult(
            entries =
                listOf(
                    QueryResult.Entry(
                        id = "result_id1",
                        content = "Content 1",
                        similarity = 0.93f,
                        parent = null,
                        authorGID = "author A's GID",
                        storedAt = Clock.System.now().epochSeconds,
                    ),
                    QueryResult.Entry(
                        id = "result_id2",
                        content = "Content 2",
                        similarity = 0.93f,
                        parent = "result_id1",
                        authorGID = "author B's GID",
                        storedAt = Clock.System.now().epochSeconds,
                    ),
                ),
            queryOnId = "Query on ID",
        )
    }

    suspend fun queryOn(request: QueryOnRequest): QueryResult {
        log.debug { request }
        return QueryResult(
            entries =
                listOf(
                    QueryResult.Entry(
                        id = "result_id3",
                        content = "Content 3",
                        similarity = 0.93f,
                        parent = null,
                        authorGID = "author C's GID",
                        storedAt = Clock.System.now().epochSeconds,
                    ),
                    QueryResult.Entry(
                        id = "result_id4",
                        content = "Content 4",
                        similarity = 0.93f,
                        parent = "result_id1",
                        authorGID = "author C's GID",
                        storedAt = Clock.System.now().epochSeconds,
                    ),
                ),
            queryOnId = request.queryOnId,
        )
    }

    private companion object {
        private val log = KotlinLogging.logger { }
    }
}
