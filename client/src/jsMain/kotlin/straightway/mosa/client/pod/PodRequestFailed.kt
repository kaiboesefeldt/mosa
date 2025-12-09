package straightway.mosa.client.pod

import io.ktor.http.HttpStatusCode

class PodRequestFailed(
    val path: String,
    val payload: Any,
    val status: HttpStatusCode,
) : RuntimeException("Invalid response to $path with $payload: ${status.value}")
