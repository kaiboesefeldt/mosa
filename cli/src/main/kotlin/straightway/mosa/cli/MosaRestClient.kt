package straightway.mosa.cli

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import straightway.mosa.content.PublishRequest
import java.net.URI
import java.net.URL

class MosaRestClient(
    private val podUrl: URL,
    private val credentials: String,
    private val httpClient: HttpClient,
) {
    suspend fun publish(text: String) {
        val response =
            httpClient.post(URI("$podUrl/v1/publish").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(PublishRequest(text = text))
                header(HttpHeaders.Authorization, "Bearer $credentials")
            }
        println(response)
    }
}
