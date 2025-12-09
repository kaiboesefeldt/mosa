package straightway.mosa.client.pod

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.PodCertificate
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.SearchResult

interface PodAccess {
    suspend fun approveGid(gid: String): PodCertificate
    suspend fun publish(request: PublishRequest, jwt: String)
    suspend fun reply(request: ReplyRequest, jwt: String)
    suspend fun search(request: QueryRequest, jwt: String): SearchResult
    suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult
    suspend fun fetch(request: FetchRequest, jwt: String): FetchResult
}

class PodAccessImpl(
    private val podBase: String,
    private val httpClient: HttpClient,
): PodAccess  {
    override suspend fun approveGid(gid: String): PodCertificate {
        val path = "/v1/approvegid"
        return post(path, AuthenticationApprovalRequest(gid))
            .ensureSuccess(path, gid)
            .body()
    }

    override suspend fun publish(request: PublishRequest, jwt: String) {
        val path = "/v1/publish"
        post(path, request, jwt).ensureSuccess(path, request)
    }

    override suspend fun reply(request: ReplyRequest, jwt: String) {
        val path = "/v1/reply"
        post(path, request, jwt).ensureSuccess(path, request)
    }

    override suspend fun search(request: QueryRequest, jwt: String): SearchResult =
        post("/v1/search", request, jwt)
            .ensureSuccess("/v1/search", request)
            .body()

    override suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult =
        post("/v1/searchon", request, jwt)
            .ensureSuccess("/v1/searchon", request)
            .body()

    override suspend fun fetch(request: FetchRequest, jwt: String): FetchResult =
        post("/v1/fetch", request, jwt)
            .ensureSuccess("/v1/fetch", request)
            .body()

    private suspend fun post(path: String, payload: Any, jwt: String? = null): HttpResponse =
        httpClient.post("$podBase$path") {
            contentType(ContentType.Application.Json)
            setBody(payload)
            if (jwt != null)
                header(HttpHeaders.Authorization, "Bearer $jwt")
        }

    private fun HttpResponse.ensureSuccess(path: String, payload: Any): HttpResponse =
        apply {
            if (status.value !in 200..299) {
                throw PodRequestFailed("$podBase$path", payload, status)
            }
        }
}
