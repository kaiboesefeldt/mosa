package straightway.mosa.defense.requestguard

import io.ktor.http.HttpMethod
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ReceiveRequestBytes
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.pool.ByteArrayPool
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

data class RequestBodyLimitConfig(
    val maxBodyBytes: Long,
)

fun createMosaRequestBodyLimitPlugin(config: RequestBodyLimitConfig) =
    createApplicationPlugin("MosaRequestBodyLimit") {
        on(ReceiveRequestBytes) { call, body ->
            val request = call.request
            if (!request.path().startsWith(API_ROUTE_PREFIX)) return@on body
            if (request.httpMethod != HttpMethod.Post) return@on body
            limitRequestBody(body, config.maxBodyBytes, call.coroutineContext)
        }
    }

private fun limitRequestBody(
    body: ByteReadChannel,
    maxBodyBytes: Long,
    coroutineContext: CoroutineContext,
): ByteReadChannel {
    val limited = ByteChannel(autoFlush = true)
    CoroutineScope(coroutineContext).launch {
        val buffer = ByteArrayPool.borrow()
        var totalBytes = 0L
        try {
            while (!body.isClosedForRead) {
                when (val read = body.readAvailable(buffer)) {
                    -1 -> break
                    else -> {
                        totalBytes += read
                        if (maxBodyBytes < totalBytes) {
                            throw PayloadTooLargeException(maxBodyBytes)
                        }
                        limited.writeFully(buffer, 0, read)
                    }
                }
            }
            body.closedCause?.let { throw it }
        } catch (cause: Throwable) {
            body.cancel(cause)
            limited.cancel(cause)
            return@launch
        } finally {
            ByteArrayPool.recycle(buffer)
            if (!limited.isClosedForWrite) {
                limited.close()
            }
        }
    }
    return limited
}

private const val API_ROUTE_PREFIX = "/v1/"
