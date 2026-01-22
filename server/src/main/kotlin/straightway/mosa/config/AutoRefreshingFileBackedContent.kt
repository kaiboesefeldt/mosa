package straightway.mosa.config

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readBytes
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class AutoRefreshingFileBackedContent<T>(
    val config: Config,
    val clock: Clock,
    private val onError: (Throwable) -> Unit = {},
    private val transformer: Transformer<T>,
) {
    data class Config(
        val path: Path,
        val refreshInterval: Duration,
    )

    fun interface Transformer<T> {
        fun transform(content: ByteArray): T
    }

    suspend fun get(): T? =
        mutex.withLock {
            val oldState = state

            if (oldState.needsRefresh()) {
                runCatching {
                    readFileContent().transform(oldState.value, onError)
                }.getOrNull()?.let {
                    state = it
                }
            }

            state.value
        }

    override fun toString() = state.value?.toString() ?: "null"

    private data class State<T>(
        val value: T?,
        val nextRefreshTime: Instant,
        val previousFileModifiedTime: FileTime,
    )

    private val mutex = Mutex()
    private var state: State<T> = readFileContent().transform(null) { throw it }

    private fun State<ByteArray>.transform(
        old: T?,
        onError: (Throwable) -> Unit,
    ) = State(
        value =
            value?.let {
                try {
                    transformer.transform(it)
                } catch (e: Exception) {
                    onError(e)
                    old
                }
            },
        nextRefreshTime = nextRefreshTime,
        previousFileModifiedTime = previousFileModifiedTime,
    )

    private fun State<*>.needsRefresh(): Boolean = clock.instant().isAfter(nextRefreshTime) && isFileChanged()

    private fun State<*>.isFileChanged(): Boolean =
        config.path.exists() &&
            runCatching { previousFileModifiedTime < config.path.getLastModifiedTime() }
                .getOrDefault(false)

    private fun readFileContent(): State<ByteArray> {
        val nextRefreshTime = clock.instant().plus(config.refreshInterval.toJavaDuration())
        return if (config.path.exists()) {
            State(config.path.readBytes(), nextRefreshTime, config.path.getLastModifiedTime())
        } else {
            State(null, nextRefreshTime, FileTime.fromMillis(0))
        }
    }
}
