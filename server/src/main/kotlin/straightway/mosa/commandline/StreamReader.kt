package straightway.mosa.commandline

import kotlinx.coroutines.flow.flow

fun interface StreamReader<T> {
    suspend fun readNext(): T?
}

fun <T> StreamReader<T>.asFlow() =
    flow {
        while (true) {
            emit(readNext() ?: return@flow)
        }
    }
