package straightway.mosa.cli

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.SearchRequest
import straightway.mosa.content.SearchResult
import java.net.URI
import java.net.URL

class MosaRestClient(
    private val podUrl: URL,
    private val credentials: String,
    private val httpClient: HttpClient,
) {
    suspend fun publish(publish: PublishRequest) {
        val response =
            httpClient.post(URI("$podUrl/v1/publish").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(publish)
                header(HttpHeaders.Authorization, "Bearer $credentials")
            }
        if (!response.status.isSuccess()) {
            throw RuntimeException("Publish request ${publish} got an error response: $response")
        }
    }

    suspend fun search(query: SearchRequest): SearchResult {
        val response =
            httpClient.post(URI("$podUrl/v1/query").toURL()) {
                contentType(ContentType.Application.Json)
                setBody(query)
                header(HttpHeaders.Authorization, "Bearer $credentials")
            }

        if (!response.status.isSuccess()) {
            throw RuntimeException("Search request ${query} got an error response: $response")
        }

        return response.body<SearchResult>()
    }
}
