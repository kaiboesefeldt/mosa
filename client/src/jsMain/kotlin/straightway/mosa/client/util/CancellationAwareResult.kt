package straightway.mosa.client.util

import kotlin.coroutines.cancellation.CancellationException
import straightway.mosa.client.logging.Logger

internal fun <T> Result<T>.getOrElseLogOrThrowCancellation(
    logger: Logger,
    message: String,
    fallback: () -> T,
): T =
    getOrElse { throwable ->
        if (throwable is CancellationException) throw throwable
        logger.warn(message, throwable)
        fallback()
    }

internal fun Result<*>.onFailureLogOrThrowCancellation(
    logger: Logger,
    message: String,
) {
    onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
        logger.warn(message, throwable)
    }
}
