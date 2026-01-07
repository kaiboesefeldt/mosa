package straightway.mosa.defense.requestguard

import io.ktor.http.HttpMethod
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ReceiveRequestBytes
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.io.Buffer

data class RequestBodyLimitConfig(
    val maxBodyBytes: Long,
)

fun createMosaRequestBodyLimitPlugin(config: RequestBodyLimitConfig) =
    createApplicationPlugin("MosaRequestBodyLimit") {
        on(ReceiveRequestBytes) { call, body ->
            val request = call.request
            if (!request.path().startsWith(API_ROUTE_PREFIX)) return@on body
            if (request.httpMethod != HttpMethod.Post) return@on body
            limitRequestBody(body, config.maxBodyBytes)
        }
    }

private fun limitRequestBody(
    body: ByteReadChannel,
    maxBodyBytes: Long,
): ByteReadChannel = LimitedByteReadChannel(body, maxBodyBytes)

private const val API_ROUTE_PREFIX = "/v1/"

@OptIn(InternalAPI::class)
private class LimitedByteReadChannel(
    private val delegate: ByteReadChannel,
    private val maxBodyBytes: Long,
) : ByteReadChannel {
    private val buffer = Buffer()
    private var bufferedBytes = 0L
    private var consumedBytes = 0L

    override val closedCause: Throwable?
        get() = delegate.closedCause

    override val isClosedForRead: Boolean
        get() = buffer.exhausted() && delegate.isClosedForRead

    @InternalAPI
    override val readBuffer: Buffer
        get() {
            transferFromDelegate()
            return buffer
        }

    @OptIn(InternalAPI::class)
    override suspend fun awaitContent(min: Int): Boolean {
        if (readBuffer.size >= min) {
            return true
        }
        if (delegate.awaitContent(min)) {
            transferFromDelegate()
            return true
        }
        return false
    }

    override fun cancel(cause: Throwable?) {
        delegate.cancel(cause)
        buffer.close()
    }

    private fun transferFromDelegate() {
        updateConsumed()
        val appended = buffer.transferFrom(delegate.readBuffer)
        if (appended <= 0L) {
            return
        }
        bufferedBytes += appended
        if (maxBodyBytes < consumedBytes + bufferedBytes) {
            val cause = PayloadTooLargeException(maxBodyBytes)
            delegate.cancel(cause)
            throw cause
        }
    }

    private fun updateConsumed() {
        consumedBytes += bufferedBytes - buffer.size
        bufferedBytes = buffer.size
    }
}
