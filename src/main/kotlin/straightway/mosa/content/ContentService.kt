package straightway.mosa.content

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ContentService {
    suspend fun postContent(request: PublishRequest) {
        println(request)
    }

    suspend fun reply(request: ReplyRequest) {
        println(request)
    }

    suspend fun query(request: QueryRequest): QueryResult {
        println(request)
        return QueryResult(
            entries = listOf(
                QueryResult.Entry(
                    id = "result_id1",
                    content = "Content 1",
                    similarity = 0.93f,
                    parent = null,
                    authorGID = "author A's GID",
                    storedAt = Clock.System.now().epochSeconds
                ),
                QueryResult.Entry(
                    id = "result_id2",
                    content = "Content 2",
                    similarity = 0.93f,
                    parent = "result_id1",
                    authorGID = "author B's GID",
                    storedAt = Clock.System.now().epochSeconds
                )
            ),
            queryOnId = "Query on ID"
        )
    }
    suspend fun queryOn(request: QueryOnRequest): QueryResult  {
        println(request)
        return QueryResult(
            entries = listOf(
                QueryResult.Entry(
                    id = "result_id3",
                    content = "Content 3",
                    similarity = 0.93f,
                    parent = null,
                    authorGID = "author C's GID",
                    storedAt = Clock.System.now().epochSeconds
                ),
                QueryResult.Entry(
                    id = "result_id4",
                    content = "Content 4",
                    similarity = 0.93f,
                    parent = "result_id1",
                    authorGID = "author C's GID",
                    storedAt = Clock.System.now().epochSeconds
                )
            ),
            queryOnId = request.queryOnId
        )
    }
}