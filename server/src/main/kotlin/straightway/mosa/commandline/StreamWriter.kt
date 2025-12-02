package straightway.mosa.commandline

import kotlinx.coroutines.flow.Flow

fun interface StreamWriter<T> {
    suspend fun write(obj: T)
}

suspend fun <T> StreamWriter<T>.writeAll(flow: Flow<T>) = flow.collect { write(it) }
