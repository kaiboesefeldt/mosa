package straightway.mosa.client.pod

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import straightway.mosa.auth.AuthenticationApprovalRequest
import straightway.mosa.auth.PodCertificateResponse
import straightway.mosa.content.PublishRequest
import straightway.mosa.content.QueryRequest
import straightway.mosa.content.QueryOnRequest
import straightway.mosa.content.ReplyRequest
import straightway.mosa.content.FetchRequest
import straightway.mosa.content.FetchResult
import straightway.mosa.content.DeletePostsResult
import straightway.mosa.content.FreezeSnapshotRestoreResult
import straightway.mosa.content.SearchResult

interface PodAccess {
    suspend fun approveGid(gid: String): PodCertificateResponse
    suspend fun refreshGid(jwt: String): PodCertificateResponse
    suspend fun publish(request: PublishRequest, jwt: String)
    suspend fun reply(request: ReplyRequest, jwt: String)
    suspend fun deletePosts(jwt: String): DeletePostsResult
    suspend fun deletePost(postId: String, jwt: String): DeletePostsResult
    suspend fun exportPostsCsv(jwt: String): ByteArray
    suspend fun freezePrivacySnapshot(jwt: String): ByteArray
    suspend fun restorePrivacySnapshot(snapshot: ByteArray, jwt: String): FreezeSnapshotRestoreResult
    suspend fun search(request: QueryRequest, jwt: String): SearchResult
    suspend fun searchOn(request: QueryOnRequest, jwt: String): SearchResult
    suspend fun fetch(request: FetchRequest, jwt: String): FetchResult
    suspend fun privacyBaselineClearTerms(): String
    suspend fun privacyDefaultClearTerms(): String
    suspend fun privacyMosaClearTerms(): String
    suspend fun privacyOperatorClearTerms(): String
    suspend fun termsBaselineClearTerms(): String
    suspend fun termsDefaultClearTerms(): String
    suspend fun termsMosaClearTerms(): String
    suspend fun termsOperatorClearTerms(): String
    suspend fun termsServiceClearTerms(): String
}

class PodAccessImpl(
    private val podBase: String,
    private val httpClient: HttpClient,
): PodAccess  {
    override suspend fun approveGid(gid: String): PodCertificateResponse {
        val path = "/v1/approvegid"
        return post(path, AuthenticationApprovalRequest(gid))
            .ensureSuccess(path, gid)
            .body()
    }

    override suspend fun refreshGid(jwt: String): PodCertificateResponse {
        val path = "/v1/refreshgid"
        return post(path, jwt)
            .ensureSuccess(path, Unit)
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

    override suspend fun deletePosts(jwt: String): DeletePostsResult {
        val path = "/v1/posts"
        return delete(path, jwt).ensureSuccess(path, Unit).body()
    }

    override suspend fun deletePost(postId: String, jwt: String): DeletePostsResult {
        val path = "/v1/posts/$postId"
        return delete(path, jwt).ensureSuccess(path, postId).body()
    }

    override suspend fun exportPostsCsv(jwt: String): ByteArray =
        get("/v1/posts/export.csv", jwt)
            .ensureSuccess("/v1/posts/export.csv", Unit)
            .body()

    override suspend fun freezePrivacySnapshot(jwt: String): ByteArray =
        post("/v1/privacy/freeze", jwt)
            .ensureSuccess("/v1/privacy/freeze", Unit)
            .body()

    override suspend fun restorePrivacySnapshot(
        snapshot: ByteArray,
        jwt: String,
    ): FreezeSnapshotRestoreResult =
        post("/v1/privacy/restore", snapshot, ContentType.Application.OctetStream, jwt)
            .ensureSuccess("/v1/privacy/restore", Unit)
            .body()

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

    override suspend fun privacyBaselineClearTerms(): String =
        get("/v1/legal/privacy/baseline")
            .ensureSuccess("/v1/legal/privacy/baseline", Unit)
            .body()

    override suspend fun privacyDefaultClearTerms(): String =
        get("/v1/legal/privacy/default")
            .ensureSuccess("/v1/legal/privacy/default", Unit)
            .body()

    override suspend fun privacyMosaClearTerms(): String =
        get("/v1/legal/privacy/mosa")
            .ensureSuccess("/v1/legal/privacy/mosa", Unit)
            .body()

    override suspend fun privacyOperatorClearTerms(): String =
        get("/v1/legal/privacy/operator")
            .ensureSuccess("/v1/legal/privacy/operator", Unit)
            .body()

    override suspend fun termsBaselineClearTerms(): String =
        get("/v1/legal/terms/baseline")
            .ensureSuccess("/v1/legal/terms/baseline", Unit)
            .body()

    override suspend fun termsDefaultClearTerms(): String =
        get("/v1/legal/terms/default")
            .ensureSuccess("/v1/legal/terms/default", Unit)
            .body()

    override suspend fun termsMosaClearTerms(): String =
        get("/v1/legal/terms/mosa")
            .ensureSuccess("/v1/legal/terms/mosa", Unit)
            .body()

    override suspend fun termsOperatorClearTerms(): String =
        get("/v1/legal/terms/operator")
            .ensureSuccess("/v1/legal/terms/operator", Unit)
            .body()

    override suspend fun termsServiceClearTerms(): String =
        get("/v1/legal/terms/service")
            .ensureSuccess("/v1/legal/terms/service", Unit)
            .body()

    private suspend fun post(
        path: String,
        payload: Any,
        contentType: ContentType,
        jwt: String? = null,
    ): HttpResponse =
        httpClient.post("$podBase$path") {
            contentType(contentType)
            setBody(payload)
            if (jwt != null)
                header(HttpHeaders.Authorization, "Bearer $jwt")
        }

    private suspend fun post(path: String, payload: Any, jwt: String? = null): HttpResponse =
        post(path, payload, ContentType.Application.Json, jwt)

    private suspend fun post(path: String, jwt: String): HttpResponse =
        httpClient.post("$podBase$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $jwt")
        }

    private suspend fun delete(path: String, jwt: String): HttpResponse =
        httpClient.delete("$podBase$path") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $jwt")
        }

    private suspend fun get(path: String, jwt: String? = null): HttpResponse =
        httpClient.get("$podBase$path") {
            if (jwt != null) {
                header(HttpHeaders.Authorization, "Bearer $jwt")
            } else {
                contentType(ContentType.Application.Json)
            }
        }

    private fun HttpResponse.ensureSuccess(path: String, payload: Any): HttpResponse =
        apply {
            if (status.value !in 200..299) {
                throw PodRequestFailed("$podBase$path", payload, status)
            }
        }
}
